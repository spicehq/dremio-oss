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
package com.dremio.exec.planner.sql.parser;

import java.util.List;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.parser.SqlParserPos;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SqlDropReflection extends SqlSystemCall {

  public static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator("DROP_REFLECTION", SqlKind.OTHER_DDL) {
    @Override
    public SqlCall createCall(SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
      Preconditions.checkArgument(operands.length == 3, "SqlDropReflection.createCall() has to get 3 operands!");
      return new SqlDropReflection(pos, (SqlIdentifier) operands[0], (SqlIdentifier) operands[1], (SqlTableVersionSpec) operands[2]);
    }
  };

  private final SqlIdentifier tblName;
  private final SqlIdentifier layoutId;
  private final SqlTableVersionSpec tableVersionSpec;

  public SqlDropReflection(SqlParserPos pos, SqlIdentifier tblName, SqlIdentifier layoutId, SqlTableVersionSpec tableVersionSpec) {
    super(pos);
    this.tblName = tblName;
    this.layoutId = layoutId;
    this.tableVersionSpec = tableVersionSpec;
  }

  public SqlIdentifier getTblName() {
    return tblName;
  }

  public SqlIdentifier getLayoutId() {
    return layoutId;
  }

  public SqlTableVersionSpec getSqlTableVersionSpec() {
    return tableVersionSpec;
  }

  @Override
  public SqlOperator getOperator() {
    return OPERATOR;
  }

  @Override
  public List<SqlNode> getOperandList() {
    return ImmutableList.<SqlNode>of(tblName, layoutId, tableVersionSpec);
  }

}
