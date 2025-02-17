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
package com.dremio.exec.planner.sql;

import java.util.List;

import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.validate.SqlNameMatcher;

import com.dremio.exec.expr.fn.FunctionImplementationRegistry;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;

/**
 * Dremio's hand rolled ChainedOperatorTable
 *
 * This class composes both the DremioCompositeSqlOperatorTable and the provided FunctionImplementationRegistry
 */
public class OperatorTable implements SqlOperatorTable {
  private static final DremioCompositeSqlOperatorTable DREMIO_COMPOSITE_SQL_OPERATOR_TABLE = new DremioCompositeSqlOperatorTable();

  private final List<SqlOperator> operators;
  private final ArrayListMultimap<String, SqlOperator> opMap;

  public OperatorTable(FunctionImplementationRegistry registry) {
    operators = Lists.newArrayList();
    operators.addAll(DREMIO_COMPOSITE_SQL_OPERATOR_TABLE.getOperatorList());

    opMap = ArrayListMultimap.create();

    registry.register(this);
  }

  public void add(String name, SqlOperator op) {
    operators.add(op);
    opMap.put(name.toUpperCase(), op);
  }

  @Override
  public void lookupOperatorOverloads(SqlIdentifier opName,
                                      SqlFunctionCategory category,
                                      SqlSyntax syntax, List<SqlOperator> operatorList,
                                      SqlNameMatcher nameMatcher) {
    // don't try to evaluate operators that have non name.
    if (opName == null || opName.names == null) {
      return;
    }

    DREMIO_COMPOSITE_SQL_OPERATOR_TABLE.lookupOperatorOverloads(
      opName,
      category,
      syntax,
      operatorList,
      nameMatcher);
    if (!operatorList.isEmpty()) {
      return;
    }

    if (syntax == SqlSyntax.FUNCTION && opName.isSimple()) {
      List<SqlOperator> ops = opMap.get(opName.getSimple().toUpperCase());
      operatorList.addAll(ops);
    }
  }

  @Override
  public List<SqlOperator> getOperatorList() {
    return operators;
  }

  // Get the list of SqlOperator's with the given name.
  public List<SqlOperator> getSqlOperator(String name) {
    return opMap.get(name.toUpperCase());
  }
}
