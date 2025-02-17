/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.sabot.op.boost;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.common.AutoCloseables;
import com.dremio.common.expression.SchemaPath;
import com.dremio.common.types.TypeProtos;
import com.dremio.common.types.Types;
import com.dremio.common.util.MajorTypeHelper;
import com.dremio.exec.physical.config.BoostPOP;
import com.dremio.exec.record.BatchSchema;
import com.dremio.exec.record.VectorAccessible;
import com.dremio.exec.record.VectorContainer;
import com.dremio.exec.store.SplitAndPartitionInfo;
import com.dremio.exec.store.parquet.GlobalDictionaries;
import com.dremio.exec.store.parquet.RecordReaderIterator;
import com.dremio.io.file.BoostedFileSystem;
import com.dremio.io.file.FileSystem;
import com.dremio.sabot.exec.context.OperatorContext;
import com.dremio.sabot.exec.store.parquet.proto.ParquetProtobuf;
import com.dremio.sabot.op.scan.ScanOperator;
import com.google.common.base.Preconditions;

/**
 * Writes columns of splits in arrow format
 */
public class BoostOperator extends ScanOperator {

  private static final Logger logger = LoggerFactory.getLogger(BoostOperator.class);

  // specifies schema of BoostOperator output
  String SPLIT_COLUMN = "Split";
  String COLUMN_COLUMN = "Column";
  String BOOSTED_COLUMN = "Boosted";

  BatchSchema SCHEMA = BatchSchema.newBuilder()
      .addField(MajorTypeHelper.getFieldForNameAndMajorType(SPLIT_COLUMN, Types.optional(TypeProtos.MinorType.VARCHAR)))
      .addField(MajorTypeHelper.getFieldForNameAndMajorType(COLUMN_COLUMN, Types.optional(TypeProtos.MinorType.VARCHAR)))
      .addField(MajorTypeHelper.getFieldForNameAndMajorType(BOOSTED_COLUMN, Types.optional(TypeProtos.MinorType.BIT)))
      .setSelectionVectorMode(BatchSchema.SelectionVectorMode.NONE)
      .build();

  Field SPLIT = SCHEMA.getColumn(0);
  Field COLUMN = SCHEMA.getColumn(1);
  Field BOOSTED = SCHEMA.getColumn(2);

  private final FileSystem fs;
  private BoostedFileSystem boostedFS;
  private SplitAndPartitionInfo currentSplit;
  private final List<ArrowColumnWriter> currentWriters;
  private final List<String> dataset;
  private final List<String> columnsToBoost;

  private final VectorContainer boostOutputContainer;
  private VarCharVector splitVector;
  private VarCharVector columnVector;
  private BitVector boostedFlagVector;

  public BoostOperator(BoostPOP boostConfig,
                       OperatorContext context,
                       RecordReaderIterator readers,
                       GlobalDictionaries globalDictionaries,
                       FileSystem fileSystem) {
    super(boostConfig.asParquetSubScan(), context, readers, globalDictionaries, null, null);

    columnsToBoost = new ArrayList<>();
    for (SchemaPath column : boostConfig.getColumns()) {
      if (column.isSimplePath()) {
        columnsToBoost.add(column.getRootSegment().getPath());
      } else {
        logger.error("Skipping complex column {}", column);
      }
    }
    fs = fileSystem;
    currentWriters = new ArrayList<>();
    boostOutputContainer = context.createOutputVectorContainer();
    dataset = boostConfig.getReferencedTables().iterator().next();
  }

  @Override
  public VectorAccessible setup() throws Exception {
    splitVector = boostOutputContainer.addOrGet(SPLIT);
    columnVector = boostOutputContainer.addOrGet(COLUMN);
    boostedFlagVector = boostOutputContainer.addOrGet(BOOSTED);
    boostOutputContainer.buildSchema();

    if (columnsToBoost.isEmpty()) {
      state = State.DONE;
      return boostOutputContainer;
    }
    super.setup();
    state = State.CAN_PRODUCE;
    boostedFS = fs.getBoostedFilesystem();
    setUpNewWriters();
    return boostOutputContainer;
  }

  @Override
  public int outputData() throws Exception {
    state.is(State.CAN_PRODUCE);
    currentReader.allocate(fieldVectorMap);

    int recordCount = currentReader.next();
    if (recordCount == 0) {
      if (!readers.hasNext()) {
        // We're on the last reader, and it has no (more) rows.
        // no need to close the reader (will be done when closing the operator)
        // but we might as well release any memory that we're holding.
        outgoing.zeroVectors();
        state = State.DONE;
        outgoing.setRecordCount(0);
        return closeCurrentWritersAndProduceOutputBatch();
      }

      // There are more readers, let's close the previous one and get the next one.
      currentReader.close();
      int boostOutputRecordCount = closeCurrentWritersAndProduceOutputBatch();

      currentReader = readers.next();
      setupReader(currentReader);
      currentReader.allocate(fieldVectorMap);
      setUpNewWriters();

      return boostOutputRecordCount; // done with a split; return num of columns boosted
    }
    outgoing.setAllCount(recordCount);

    List<ArrowColumnWriter> failedWriters = new ArrayList<>();
    for (ArrowColumnWriter writer : currentWriters) {
      try {
        writer.write(recordCount);
      } catch (IOException ex) {
        logger.error("Failed to write record batch of column [{}] to boost file, skipping column", writer.column, ex);
        writer.abort();
        failedWriters.add(writer);
      }
    }
    currentWriters.removeAll(failedWriters);

    return 0;
  }

  /**
   * creates a writer per column for the new split
   *
   */
  private void setUpNewWriters() {
    ParquetProtobuf.ParquetDatasetSplitScanXAttr splitXAttr = readers.getCurrentSplitXAttr();
    Preconditions.checkArgument(splitXAttr != null, "splitXAttr can not be null");
    for (String columnName : columnsToBoost) {
      ArrowColumnWriter arrowColumnWriter;
      try {
        arrowColumnWriter = new ArrowColumnWriter(splitXAttr, columnName, context, boostedFS, dataset);
        arrowColumnWriter.setup(fieldVectorMap.get(columnName.toLowerCase()));
      } catch (IOException ex) {
        logger.debug("Failed to initialize column writer to boost column [{}] of split [{}] due to {}. Skipping column.", columnName, splitXAttr.getPath(), ex.getMessage());
        continue;
      }
      currentWriters.add(arrowColumnWriter);
    }
  }

  /**
   * File stats will be available after closing writers
   * return number of files written by writer
   *
   * @return
   * @throws Exception
   */
  private int closeCurrentWritersAndProduceOutputBatch() {
    List<ArrowColumnWriter> failedWriters = new ArrayList<>();
    for (ArrowColumnWriter currentWriter : currentWriters) {
      try {
        currentWriter.close();
        currentWriter.commit();
        logger.debug("Boosted column [{}] of split [{}]", currentWriter.column, currentWriter.splitPath);
      } catch (Exception ex) {
        logger.error("Failure while committing boost file of column [{}] of split [{}]", currentWriter.column, currentWriter.splitPath);
        failedWriters.add(currentWriter);
      }
    }

    currentWriters.removeAll(failedWriters);
    failedWriters.clear();

    boostOutputContainer.allocateNew();

    int i = 0;
    for (ArrowColumnWriter writer : currentWriters) {
      splitVector.setSafe(i, writer.splitPath.getBytes(UTF_8), 0, writer.splitPath.length());
      columnVector.setSafe(i, writer.column.getBytes(UTF_8), 0, writer.column.length());
      boostedFlagVector.setSafe(i, 1);
      i++;
    }
    currentWriters.clear();
    return boostOutputContainer.setAllCount(i);
  }

  @Override
  public void close() throws Exception {
    super.close();
    AutoCloseables.close(boostOutputContainer, currentWriters);
  }

  /**
   * This gets called when ScanOperator.close() is invoked from this.close()
   */
  @Override
  protected void onScanDone() {}
}
