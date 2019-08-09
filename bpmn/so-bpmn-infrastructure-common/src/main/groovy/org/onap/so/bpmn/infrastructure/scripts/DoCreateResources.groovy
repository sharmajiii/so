/*-
 * ============LICENSE_START=======================================================
 * ONAP - SO
 * ================================================================================
 * Copyright (C) 2018 Huawei Technologies Co., Ltd. All rights reserved.
 * ================================================================================
 * Modifications Copyright (c) 2019 Samsung
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 * ============LICENSE_END=========================================================
 */

package org.onap.so.bpmn.infrastructure.scripts


import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.apache.http.util.EntityUtils
import org.onap.so.bpmn.common.resource.InstanceResourceList
import org.onap.so.bpmn.common.scripts.CatalogDbUtilsFactory
import org.onap.so.bpmn.core.domain.ModelInfo
import org.onap.so.bpmn.core.domain.ResourceType
import org.onap.so.bpmn.infrastructure.properties.BPMNProperties
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpResponse
import org.camunda.bpm.engine.delegate.BpmnError
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.json.JSONObject
import org.onap.so.bpmn.common.recipe.BpmnRestClient
import org.onap.so.bpmn.common.recipe.ResourceInput
import org.onap.so.bpmn.common.scripts.AbstractServiceTaskProcessor
import org.onap.so.bpmn.common.scripts.CatalogDbUtils
import org.onap.so.bpmn.common.scripts.ExceptionUtil
import org.onap.so.bpmn.core.domain.AllottedResource
import org.onap.so.bpmn.core.domain.NetworkResource
import org.onap.so.bpmn.core.domain.Resource
import org.onap.so.bpmn.core.domain.ServiceDecomposition
import org.onap.so.bpmn.core.domain.VnfResource
import org.onap.so.bpmn.core.json.JsonUtils
import org.onap.so.bpmn.common.resource.ResourceRequestBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Type


/**
 * This groovy class supports the <class>DoCreateResources.bpmn</class> process.
 *
 * Inputs:
 * @param - msoRequestId
 * @param - globalSubscriberId - O
 * @param - subscriptionServiceType - O
 * @param - serviceInstanceId
 * @param - serviceInstanceName - O
 * @param - serviceInputParams (should contain aic_zone for serviceTypes TRANSPORT,ATM)
 * @param - sdncVersion
 *
 * @param - addResourceList
 *
 * Outputs:
 * @param - WorkflowException
 */
public class DoCreateResources extends AbstractServiceTaskProcessor{
    private static final Logger logger = LoggerFactory.getLogger( DoCreateResources.class)

    ExceptionUtil exceptionUtil = new ExceptionUtil()
    JsonUtils jsonUtil = new JsonUtils()
    CatalogDbUtils catalogDbUtils = new CatalogDbUtilsFactory().create()

     void preProcessRequest(DelegateExecution execution) {
        logger.trace("preProcessRequest ")
        String msg = ""

        List addResourceList = execution.getVariable("addResourceList")
        if (addResourceList == null) {
            msg = "Input addResourceList is null"
            logger.info(msg)
            exceptionUtil.buildAndThrowWorkflowException(execution, 500, msg)
        }
        else if (addResourceList.size() == 0) {
            msg = "No resource in addResourceList"
            logger.info(msg)
        }
        logger.trace("Exit preProcessRequest ")
    }

     void sequenceResoure(DelegateExecution execution) {
        logger.trace("Start sequenceResoure Process ")

        String incomingRequest = execution.getVariable("uuiRequest")
        String serviceModelUuid = jsonUtil.getJsonValue(incomingRequest,"service.serviceUuid")

        List<Resource> addResourceList = execution.getVariable("addResourceList")

        List<NetworkResource> networkResourceList = new ArrayList<NetworkResource>()

        List<Resource> sequencedResourceList = new ArrayList<Resource>()

        String serviceDecompose = execution.getVariable("serviceDecomposition")
        String serviceModelName = jsonUtil.getJsonValue(serviceDecompose, "serviceResources.modelInfo.modelName")

        // get Sequence from properties
        def resourceSequence = BPMNProperties.getResourceSequenceProp(serviceModelName)

        // get Sequence from csar(model)
        if(resourceSequence == null) {
            resourceSequence = ResourceRequestBuilder.getResourceSequence(serviceModelUuid)
            logger.info("Get Sequence from csar : " + resourceSequence)
        }

        if(resourceSequence != null) {
            for (resourceType in resourceSequence) {
                for (resource in addResourceList) {
                    if (StringUtils.containsIgnoreCase(resource.getModelInfo().getModelName(), resourceType)) {


                        // if resource type is vnfResource then check for groups also
                        // Did not use continue because if same model type is used twice
                        // then we would like to add it twice for processing
                        // ex-1. S{ V1{G1, G2}} --> S{ V1{G1, G1, G2}}
                        // ex-2. S{ V1{G1, G2}} --> S{ V1{G1, G2, G2, G2} V1 {G1, G1, G2}}
                        if ((resource.getResourceType() == ResourceType.VNF) && (resource instanceof VnfResource)) {

                            // check the size of VNF/Group list from UUI
                            List<Resource> sequencedInstanceResourceList = InstanceResourceList.getInstanceResourceList((VnfResource) resource, incomingRequest)
                            sequencedResourceList.addAll(sequencedInstanceResourceList)
                        } else {
                            sequencedResourceList.add(resource)
                        }
                        if (resource instanceof NetworkResource) {
                            networkResourceList.add(resource)
                        }
                    }
                }
            }
        } else {

            //define sequenced resource list, we deploy vf first and then network and then ar
            //this is default sequence
            List<VnfResource> vnfResourceList = new ArrayList<VnfResource>()
            List<AllottedResource> arResourceList = new ArrayList<AllottedResource>()

            for (Resource rc : addResourceList){
                if (rc instanceof VnfResource) {
                    // check the size of VNF/Group list from UUI
                    List<Resource> sequencedGroupResourceList = InstanceResourceList.getInstanceResourceList((VnfResource) rc, incomingRequest)
                    vnfResourceList.addAll(sequencedGroupResourceList)
                } else if (rc instanceof NetworkResource) {
                    networkResourceList.add(rc)
                } else if (rc instanceof AllottedResource) {
                    arResourceList.add(rc)
                }
            }
            sequencedResourceList.addAll(vnfResourceList)
            sequencedResourceList.addAll(networkResourceList)
            sequencedResourceList.addAll(arResourceList)
        }

        String isContainsWanResource = networkResourceList.isEmpty() ? "false" : "true"
        //if no networkResource, get SDNC config from properties file
        if( "false".equals(isContainsWanResource)) {
            String serviceNeedSDNC = "mso.workflow.custom." + serviceModelName + ".sdnc.need"
            isContainsWanResource = BPMNProperties.getProperty(serviceNeedSDNC, isContainsWanResource)
        }

        execution.setVariable("isContainsWanResource", isContainsWanResource)
        execution.setVariable("currentResourceIndex", 0)
        execution.setVariable("sequencedResourceList", sequencedResourceList)
        logger.info("sequencedResourceList: " + sequencedResourceList)
        logger.trace("COMPLETED sequenceResoure Process ")
    }

     void prepareServiceTopologyRequest(DelegateExecution execution) {

        logger.trace("======== Start prepareServiceTopologyRequest Process ======== ")

        String serviceDecompose = execution.getVariable("serviceDecomposition")

        execution.setVariable("operationType", "create")
        execution.setVariable("resourceType", "")

        String serviceInvariantUuid = jsonUtil.getJsonValue(serviceDecompose, "serviceResources.modelInfo.modelInvariantUuid")
        String serviceUuid = jsonUtil.getJsonValue(serviceDecompose, "serviceResources.modelInfo.modelUuid")
        String serviceModelName = jsonUtil.getJsonValue(serviceDecompose, "serviceResources.modelInfo.modelName")

        execution.setVariable("modelInvariantUuid", serviceInvariantUuid)
        execution.setVariable("modelUuid", serviceUuid)
        execution.setVariable("serviceModelName", serviceModelName)

        logger.trace("======== End prepareServiceTopologyRequest Process ======== ")
    }

     void getCurrentResoure(DelegateExecution execution){
        logger.trace("Start getCurrentResoure Process ")
        def currentIndex = execution.getVariable("currentResourceIndex")
        List<Resource> sequencedResourceList = execution.getVariable("sequencedResourceList")
        Resource currentResource = sequencedResourceList.get(currentIndex)
        execution.setVariable("resourceType", currentResource.getModelInfo().getModelName())
        logger.info("Now we deal with resource:" + currentResource.getModelInfo().getModelName())
        logger.trace("COMPLETED getCurrentResource Process ")
    }

     void parseNextResource(DelegateExecution execution){
        logger.trace("Start parseNextResource Process ")
        def currentIndex = execution.getVariable("currentResourceIndex")
        def nextIndex =  currentIndex + 1
        execution.setVariable("currentResourceIndex", nextIndex)
        List<Resource> sequencedResourceList = execution.getVariable("sequencedResourceList")
        if(nextIndex >= sequencedResourceList.size()){
            execution.setVariable("allResourceFinished", "true")
        }else{
            execution.setVariable("allResourceFinished", "false")
        }
        logger.trace("COMPLETED parseNextResource Process ")
    }

     void prepareResourceRecipeRequest(DelegateExecution execution){
        logger.trace("Start prepareResourceRecipeRequest Process ")
        ResourceInput resourceInput = new ResourceInput()
        String serviceInstanceName = execution.getVariable("serviceInstanceName")
        String resourceType = execution.getVariable("resourceType")
        String resourceInstanceName = resourceType + "_" + serviceInstanceName
        resourceInput.setResourceInstanceName(resourceInstanceName)
        logger.info("Prepare Resource Request resourceInstanceName:" + resourceInstanceName)
        String globalSubscriberId = execution.getVariable("globalSubscriberId")
        String serviceType = execution.getVariable("serviceType")
        String serviceInstanceId = execution.getVariable("serviceInstanceId")
        String operationId = execution.getVariable("operationId")
        String operationType = "createInstance"
        resourceInput.setGlobalSubscriberId(globalSubscriberId)
        resourceInput.setServiceType(serviceType)
        resourceInput.setServiceInstanceId(serviceInstanceId)
        resourceInput.setOperationId(operationId)
        resourceInput.setOperationType(operationType)
        def currentIndex = execution.getVariable("currentResourceIndex")
        List<Resource> sequencedResourceList = execution.getVariable("sequencedResourceList")
        Resource currentResource = sequencedResourceList.get(currentIndex)
        resourceInput.setResourceModelInfo(currentResource.getModelInfo())
        resourceInput.getResourceModelInfo().setModelType(currentResource.getResourceType().toString())
        ServiceDecomposition serviceDecomposition = execution.getVariable("serviceDecomposition")

        if (currentResource.getResourceType() == ResourceType.VNF) {
            execution.setVariable("vfModelInfo", currentResource.getModelInfo())
        }

        resourceInput.setVfModelInfo(execution.getVariable("vfModelInfo") as ModelInfo)
        String vnfId = execution.getVariable("vnf-id")
        if (vnfId != null) {
            resourceInput.setVnfId(vnfId)
        }


        resourceInput.setServiceModelInfo(serviceDecomposition.getModelInfo())

        String incomingRequest = execution.getVariable("uuiRequest")
        //set the requestInputs from template  To Be Done
        String uuiServiceParameters = jsonUtil.getJsonValue(incomingRequest, "service.parameters")

        // current vfdata holds information for preparing input for resource
        // e.g. it will hold
        // { top_level_list_name, second_level_list_name, top_index, second_index, last processed node}
        Map<String, Object> currentVFData = (Map) execution.getVariable("currentVFData")

        if (null == currentVFData) {
            currentVFData = new HashMap<>()
        }
        String resourceParameters = ResourceRequestBuilder.buildResourceRequestParameters(execution, currentResource, uuiServiceParameters, currentVFData)
        resourceInput.setResourceParameters(resourceParameters)
        resourceInput.setRequestsInputs(incomingRequest)
        execution.setVariable("resourceInput", resourceInput.toString())
        execution.setVariable("resourceModelUUID", resourceInput.getResourceModelInfo().getModelUuid())
        execution.setVariable("currentVFData",currentVFData)
        logger.trace("COMPLETED prepareResourceRecipeRequest Process ")
    }

     void executeResourceRecipe(DelegateExecution execution){
        logger.trace("Start executeResourceRecipe Process ")

        try {
            String requestId = execution.getVariable("msoRequestId")
            String serviceInstanceId = execution.getVariable("serviceInstanceId")
            String serviceType = execution.getVariable("serviceType")
            String resourceInput = execution.getVariable("resourceInput")
            String resourceModelUUID = execution.getVariable("resourceModelUUID")

            // requestAction is action, not opertiontype
            //String requestAction = resourceInput.getOperationType()
            String requestAction = "createInstance"
            JSONObject resourceRecipe = catalogDbUtils.getResourceRecipe(execution, resourceModelUUID, requestAction)

            if (resourceRecipe != null) {
                String recipeURL = BPMNProperties.getProperty("bpelURL", "http://so-bpmn-infra.onap:8081") + resourceRecipe.getString("orchestrationUri")
                int recipeTimeOut = resourceRecipe.getInt("recipeTimeout")
                String recipeParamXsd = resourceRecipe.get("paramXSD")

                BpmnRestClient bpmnRestClient = new BpmnRestClient()
                HttpResponse resp = bpmnRestClient.post(recipeURL, requestId, recipeTimeOut, requestAction, serviceInstanceId, serviceType, resourceInput, recipeParamXsd)

                def currentIndex = execution.getVariable("currentResourceIndex")
                List<Resource> sequencedResourceList = execution.getVariable("sequencedResourceList") as List<Resource>
                Resource currentResource = sequencedResourceList.get(currentIndex)
                if(ResourceType.VNF == currentResource.getResourceType()) {
                    if (resp.getStatusLine().getStatusCode() > 199 && resp.getStatusLine().getStatusCode() < 300) {
                        String responseString = EntityUtils.toString(resp.getEntity(), "UTF-8")
                        if (responseString != null) {
                            Gson gson = new Gson()
                            Type type = new TypeToken<Map<String, String>>() {}.getType()
                            Map<String, Object> map = gson.fromJson(responseString, type)
                            Map<String, String> map1 = gson.fromJson(map.get("response"), type)
                            execution.setVariable("vnf-id",map1.get("vnf-id"))
                        }
                    }
                }
            } else {
                String exceptionMessage = "Resource receipe is not found for resource modeluuid: " + resourceModelUUID
                logger.trace(exceptionMessage)
                exceptionUtil.buildAndThrowWorkflowException(execution, 500, exceptionMessage)
            }

            logger.trace("======== end executeResourceRecipe Process ======== ")
        }catch(BpmnError b){
            logger.debug("Rethrowing MSOWorkflowException")
            throw b
        }catch(Exception e){
            logger.debug("Error occured within DoCreateResources executeResourceRecipe method: " + e)
            exceptionUtil.buildAndThrowWorkflowException(execution, 2500, "Internal Error - Occured during DoCreateResources executeResourceRecipe Catalog")
        }
    }

     void postConfigRequest(DelegateExecution execution){
        //now do noting
        ServiceDecomposition serviceDecomposition = execution.getVariable("serviceDecomposition")
        for (VnfResource resource : serviceDecomposition.vnfResources) {
            resource.setOrchestrationStatus("Active")
        }
        execution.setVariable("serviceDecomposition", serviceDecomposition)
    }
}
