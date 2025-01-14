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
package com.dremio.exec.maestro;

import java.util.List;

import com.dremio.common.exceptions.ExecutionSetupException;
import com.dremio.exec.ops.QueryContext;
import com.dremio.exec.physical.PhysicalPlan;
import com.dremio.exec.proto.UserBitShared;
import com.dremio.exec.proto.UserBitShared.QueryId;
import com.dremio.exec.work.SafeExit;
import com.dremio.exec.work.foreman.CompletionListener;
import com.dremio.options.OptionManager;
import com.dremio.options.Options;
import com.dremio.resource.GroupResourceInformation;
import com.dremio.resource.ResourceSchedulingProperties;
import com.dremio.resource.exception.ResourceAllocationException;
import com.dremio.sabot.rpc.ExecToCoordStatusHandler;
import com.dremio.service.Service;

/**
 * A service that handles execution planning and interactions with executors during the query
 * lifecycle.
 */
@Options
public interface MaestroService extends Service, SafeExit {

  /**
   * Execute the query specified by the physical plan. Includes :
   * - reserving resources
   * - parallelization of the plan
   * - propagate the fragments to the executor nodes
   * - monitor completion of fragments
   * - release reserved resources.
   *
   * @param queryId the query id (includes attempt number)
   * @param context query context
   * @param physicalPlan physical plan for the query
   * @param runInSameThread if true, the parallelization will run in the same thread.
   * @param observer observer to notify on state changes, and progress.
   * @param listener listener to notify on completion or failures.
   *
   * @throws ExecutionSetupException failure in execution planning
   * @throws ResourceAllocationException failure in resource allocation
   */
  void executeQuery(
    QueryId queryId,
    QueryContext context,
    PhysicalPlan physicalPlan,
    boolean runInSameThread,
    MaestroObserver observer,
    CompletionListener listener)
    throws ExecutionSetupException, ResourceAllocationException;

  /**
   * Interrupts the execution if in wait states. Different stages of query execution require(s) different action as
   * they may be waiting/blocking for different resources.
   *
   * @param queryId Id of the query whose execution needs to be interrupted
   * @param currentStage current Stage of the query (Interrupt actions may depend on the stage of the query)
   */
  void interruptExecutionInWaitStates(QueryId queryId, UserBitShared.AttemptEvent.State currentStage);

  /**
   * Cancel a previously triggered query.
   *
   * @param queryId the query id (includes attempt number).
   */
  void cancelQuery(QueryId queryId);

  /**
   * Get the count of active queries.
   */
  int getActiveQueryCount();

  /* Get the resource information for the group (cluster or engine).
   *
   * @param queryContext
   * @return resource information.
   */
  GroupResourceInformation getGroupResourceInformation(OptionManager optionManager,
                                                       ResourceSchedulingProperties resourceSchedulingProperties) throws ResourceAllocationException;

  /**
   * Get the rpc handler for status msgs from executor nodes.
   * @return rpc handler.
   */
  ExecToCoordStatusHandler getExecStatusHandler();


  /**
   * @return list of query Ids for all active execution foremen.
   */
  List<QueryId> getActiveQueryIds();
}
