/*-
 * Copyright (C) 2021 Bell Canada. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onap.so.bpmn.infrastructure.service.composition;

import static org.onap.so.bpmn.infrastructure.service.composition.ServiceCompositionConstants.CHILD_SVC_REQ_ERROR;
import static org.onap.so.bpmn.infrastructure.service.composition.ServiceCompositionConstants.CHILD_SVC_REQ_PAYLOAD;
import static org.onap.so.bpmn.infrastructure.service.composition.ServiceCompositionConstants.CHILD_SVC_REQ_ID;
import static org.onap.so.bpmn.infrastructure.service.composition.ServiceCompositionConstants.CHILD_SVC_INSTANCE_ID;
import static org.onap.so.bpmn.infrastructure.service.composition.ServiceCompositionConstants.CHILD_SVC_REQ_CORRELATION_ID;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.onap.logging.filter.base.ONAPComponents;
import org.onap.so.bpmn.common.BuildingBlockExecution;
import org.onap.so.bpmn.servicedecomposition.entities.ResourceKey;
import org.onap.so.client.exception.ExceptionBuilder;
import org.onap.so.client.orchestration.ApiHandlerClient;
import org.onap.so.serviceinstancebeans.ServiceInstancesRequest;
import org.onap.so.serviceinstancebeans.ServiceInstancesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CreateChildServiceBB {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected ExceptionBuilder exceptionBuilder;

    @Autowired
    private ApiHandlerClient apiHandlerClient;

    public void buildRequest(final BuildingBlockExecution buildingBlockExecution) {
        try {
            log.info("Building Create Service Request");
            Map<ResourceKey, String> lookupMap = buildingBlockExecution.getLookupMap();
            String childSvcInstanceName = lookupMap.get(ResourceKey.CHILD_SERVICE_INSTANCE_NAME);
            Objects.requireNonNull(childSvcInstanceName, "Child service instance name is required");

            ServiceInstancesRequest sir = ChildServiceRequestBuilder
                    .getInstance(buildingBlockExecution, childSvcInstanceName)
                    .setParentRequestId(
                            buildingBlockExecution.getGeneralBuildingBlock().getRequestContext().getMsoRequestId())
                    .setCorrelationId(UUID.randomUUID().toString()).build();
            buildingBlockExecution.setVariable(CHILD_SVC_REQ_PAYLOAD, sir);
        } catch (Exception e) {
            exceptionBuilder.buildAndThrowWorkflowException(buildingBlockExecution, 10002, e.getMessage(),
                    ONAPComponents.SO);
        }
    }

    public void sendRequest(final BuildingBlockExecution buildingBlockExecution) throws Exception {
        try {
            buildingBlockExecution.getLookupMap();
            ServiceInstancesRequest sir = buildingBlockExecution.getVariable(CHILD_SVC_REQ_PAYLOAD);
            log.info("Sending Create Service Request: \n{}", sir.toString());
            buildingBlockExecution.setVariable(CHILD_SVC_REQ_CORRELATION_ID,
                    sir.getRequestDetails().getRequestInfo().getCorrelator());

            ServiceInstancesResponse response = apiHandlerClient.createServiceInstance(sir);
            buildingBlockExecution.setVariable(CHILD_SVC_REQ_ID, response.getRequestReferences().getRequestId());
            buildingBlockExecution.setVariable(CHILD_SVC_INSTANCE_ID, response.getRequestReferences().getInstanceId());
        } catch (Exception e) {
            exceptionBuilder.buildAndThrowWorkflowException(buildingBlockExecution, 10003, e.getMessage(),
                    ONAPComponents.SO);
        }
    }

    public void handleFailure(final BuildingBlockExecution buildingBlockExecution) {
        Map<ResourceKey, String> lookupMap = buildingBlockExecution.getLookupMap();
        String childSvcInstanceName = lookupMap.get(ResourceKey.CHILD_SERVICE_INSTANCE_NAME);
        String childErrorMessage = buildingBlockExecution.getVariable(CHILD_SVC_REQ_ERROR);
        String errorMessage =
                String.format("Failed creating child service %s %s", childSvcInstanceName, childErrorMessage);
        exceptionBuilder.buildAndThrowWorkflowException(buildingBlockExecution, 10001, errorMessage, ONAPComponents.SO);
    }

}
