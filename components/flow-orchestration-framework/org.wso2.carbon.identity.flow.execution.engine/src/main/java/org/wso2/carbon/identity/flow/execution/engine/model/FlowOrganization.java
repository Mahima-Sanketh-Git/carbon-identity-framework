/*
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.flow.execution.engine.model;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;

/**
 * This class is responsible for holding the organization profile of the current organization in the flow.
 */
public class FlowOrganization implements Serializable {

    private static final long serialVersionUID = -6069004467814244409L;
    private static final Log LOG = LogFactory.getLog(FlowOrganization.class);

    private String organizationName;
    private String organizationHandle;
    private String creatorId;
    private String flowState;

    public String getOrganizationName() {

        return organizationName;
    }

    public void setOrganizationName(String organizationName) {

        this.organizationName = organizationName;
    }

    public String getOrganizationHandle() {

        return organizationHandle;
    }

    public void setOrganizationHandle(String organizationHandle) {

        this.organizationHandle = organizationHandle;
    }

    public String getCreatorId() {

        return creatorId;
    }

    public void setCreatorId(String creatorId) {

        this.creatorId = creatorId;
    }

    public String getFlowState() {

        return flowState;
    }

    public void setFlowState(String flowState) {

        this.flowState = flowState;
    }
}
