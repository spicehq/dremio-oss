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
package com.dremio.exec.planner.acceleration.substitution;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalCorrelate;

import com.dremio.exec.planner.RoutingShuttle;

/**
 * Shuttle that tells you whether LogicalCorrelate exists in the RelNode
 */
class CorrelateFinder extends RoutingShuttle {

  private boolean foundCorrelate = false;

  public boolean isFoundCorrelate() {
    return foundCorrelate;
  }

  @Override
  public RelNode visit(RelNode other) {
    return super.visit(other);
  }

  @Override
  public RelNode visit(LogicalCorrelate correlate) {
    foundCorrelate = true;
    return correlate; // No need to recurse any further.  We found our first completion state.
  }

}
