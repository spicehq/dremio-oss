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
package com.dremio.sabot.op.sender.partition;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.dremio.common.AutoCloseables;
import com.dremio.exec.record.VectorAccessible;
import com.dremio.exec.testing.ControlsInjector;
import com.dremio.exec.testing.ControlsInjectorFactory;
import com.dremio.exec.testing.CountDownLatchInjection;
import com.dremio.sabot.exec.context.OperatorContext;
import com.dremio.sabot.exec.context.OperatorStats;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

/**
 * Decorator class to hide multiple Partitioner existence from the caller
 * since this class involves multithreaded processing of incoming batches
 * as well as flushing it needs special handling of OperatorStats - stats
 * since stats are not suitable for use in multithreaded environment
 * The algorithm to figure out processing versus wait time is based on following formula:
 * totalWaitTime = totalAllPartitionersProcessingTime - max(sum(processingTime) by partitioner)
 */
public class PartitionerDecorator implements AutoCloseable {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PartitionerDecorator.class);
  private static final ControlsInjector injector = ControlsInjectorFactory.getInjector(PartitionerDecorator.class);

  private List<Partitioner> partitioners;
  private final String tName;
  private final String childThreadPrefix;
  private final ExecutorService executor;
  private final OperatorContext context;

  public PartitionerDecorator(List<Partitioner> partitioners, OperatorStats stats, OperatorContext context) {
    this.partitioners = partitioners;
    this.context = context;
    this.executor = context.getExecutor();
    this.tName = Thread.currentThread().getName();
    this.childThreadPrefix = "Partitioner-" + tName + "-";
  }

  /**
   * partitionBatch - decorator method to call real Partitioner(s) to process incoming batch
   * uses either threading or not threading approach based on number Partitioners
   * @param incoming
   * @throws IOException
   */
  public void partitionBatch(final VectorAccessible incoming) throws IOException {
    executeMethodLogic(new PartitionBatchHandlingClass(incoming));
  }

  /**
   * finish partitioner work.
   * @throws IOException
   */
  public void finishWork() throws IOException {
    executeMethodLogic(new TerminationHandler());
  }

  /**
   * decorator method to call multiple Partitioners close()
   */
  @Override
  public void close() throws Exception {
    AutoCloseables.close(partitioners);
  }

  /**
   * Helper method to get PartitionOutgoingBatch based on the index
   * since we may have more then one Partitioner
   * As number of Partitioners should be very small AND this method it used very rarely,
   * so it is OK to loop in order to find right partitioner
   * @param index - index of PartitionOutgoingBatch
   * @return PartitionOutgoingBatch
   */
  public PartitionOutgoingBatch getOutgoingBatches(int index) {
    for (Partitioner part : partitioners ) {
      PartitionOutgoingBatch outBatch = part.getOutgoingBatch(index);
      if ( outBatch != null ) {
        return outBatch;
      }
    }
    return null;
  }

  @VisibleForTesting
  protected List<Partitioner> getPartitioners() {
    return partitioners;
  }

  /**
   * Helper to execute the different methods wrapped into same logic
   * @param iface
   * @throws IOException
   */
  protected void executeMethodLogic(final PartitionTask iface) throws IOException {
    if (partitioners.size() == 1 ) {
      // no need for threads
      iface.execute(partitioners.get(0));
      return;
    }

    // start waiting on main stats to adjust by sum(max(processing)) at the end
    final CountDownLatch latch = new CountDownLatch(partitioners.size());
    final List<CustomRunnable> runnables = Lists.newArrayList();
    final List<Future<?>> taskFutures = Lists.newArrayList();
    CountDownLatchInjection testCountDownLatch = null;
    try {
      // To simulate interruption of main fragment thread and interrupting the partition threads, create a
      // CountDownInject patch. Partitioner threads await on the latch and main fragment thread counts down or
      // interrupts waiting threads. This makes sures that we are actually interrupting the blocked partitioner threads.
      testCountDownLatch = injector.getLatch(context.getExecutionControls(), "partitioner-sender-latch");
      testCountDownLatch.initialize(1);

      for (final Partitioner part : partitioners) {
        final CustomRunnable runnable = new CustomRunnable(childThreadPrefix, latch, iface, part, testCountDownLatch);
        runnables.add(runnable);
        taskFutures.add(executor.submit(runnable));
      }

      while (true) {
        try {
          // Wait for main fragment interruption.
          injector.injectInterruptiblePause(context.getExecutionControls(), "wait-for-fragment-interrupt", logger);

          // If there is no pause inserted at site "wait-for-fragment-interrupt", release the latch.
          injector.getLatch(context.getExecutionControls(), "partitioner-sender-latch").countDown();

          latch.await();
          break;
        } catch (final InterruptedException e) {
          logger.debug("Interrupting partitioner threads. Fragment thread {}", tName);
          for(Future<?> f : taskFutures) {
            f.cancel(true);
          }
          break;
        }
      }

      IOException excep = null;
      for (final CustomRunnable runnable : runnables ) {
        IOException myException = runnable.getException();
        if ( myException != null ) {
          if ( excep == null ) {
            excep = myException;
          } else {
            excep.addSuppressed(myException);
          }
        }
      }
      if ( excep != null ) {
        throw excep;
      }
    } finally {
      // Done with the latch, close it.
      if (testCountDownLatch != null) {
        testCountDownLatch.close();
      }
    }
  }

  /**
   * Helper interface to generalize functionality executed in the thread
   * since it is absolutely the same for partitionBatch and flushOutgoingBatches
   * protected is for testing purposes
   */
  protected interface PartitionTask {
    void execute(Partitioner partitioner) throws IOException;
  }

  /**
   * Class to handle running partitionBatch method
   *
   */
  private static class PartitionBatchHandlingClass implements PartitionTask {

    private final VectorAccessible incoming;

    public PartitionBatchHandlingClass(VectorAccessible incoming) {
      this.incoming = incoming;
    }

    @Override
    public void execute(Partitioner part) throws IOException {
      part.partitionBatch(incoming);
    }
  }

  /**
   * Class to handle running flushOutgoingBatches method
   *
   */
  private static class TerminationHandler implements PartitionTask {

    public TerminationHandler() {
    }

    @Override
    public void execute(Partitioner part) throws IOException {
      part.flushOutgoingBatches();
      part.sendTermination();
    }
  }

  /**
   * Helper class to wrap Runnable with customized naming
   * Exception handling
   *
   */
  private static class CustomRunnable implements Runnable {

    private final String parentThreadName;
    private final CountDownLatch latch;
    private final PartitionTask iface;
    private final Partitioner part;
    private CountDownLatchInjection testCountDownLatch;

    private volatile IOException exp;

    public CustomRunnable(final String parentThreadName, final CountDownLatch latch, final PartitionTask iface,
        final Partitioner part, CountDownLatchInjection testCountDownLatch) {
      this.parentThreadName = parentThreadName;
      this.latch = latch;
      this.iface = iface;
      this.part = part;
      this.testCountDownLatch = testCountDownLatch;
    }

    @Override
    public void run() {
      // Test only - Pause until interrupted by fragment thread
      try {
        testCountDownLatch.await();
      } catch (final InterruptedException e) {
        logger.debug("Test only: partitioner thread is interrupted in test countdown latch await()", e);
      }

      final Thread currThread = Thread.currentThread();
      final String currThreadName = currThread.getName();
      final OperatorStats localStats = part.getStats();
      try {
        final String newThreadName = parentThreadName + currThread.getId();
        currThread.setName(newThreadName);
        localStats.clear();
        localStats.startProcessing();
        iface.execute(part);
      } catch (IOException e) {
        exp = e;
      } finally {
        localStats.stopProcessing();
        currThread.setName(currThreadName);
        latch.countDown();
      }
    }

    public IOException getException() {
      return this.exp;
    }

    public Partitioner getPart() {
      return part;
    }
  }
 }
