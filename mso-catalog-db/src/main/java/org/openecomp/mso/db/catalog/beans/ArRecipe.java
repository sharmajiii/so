/*-
 * ============LICENSE_START=======================================================
 * ONAP - SO
 * ================================================================================
 * Copyright (C) 2018 Huawei Technologies Co., Ltd. All rights reserved.
 * ================================================================================
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
 * ============LICENSE_END=========================================================
 */

package org.openecomp.mso.db.catalog.beans;


import java.io.Serializable;

public class ArRecipe extends Recipe implements Serializable {
	private static final long serialVersionUID = 768026109321305392L;
	private String modelName;
	private String arParamXSD;
	public ArRecipe() {}

	public String getModelName() {
		return modelName;
	}
	public void setModelName(String modelName) {
		this.modelName = modelName;
	}
	
    /**
     * @return Returns the arParamXSD.
     */
    public String getArParamXSD() {
        return arParamXSD;
    }

    
    /**
     * @param arParamXSD The arParamXSD to set.
     */
    public void setArParamXSD(String arParamXSD) {
        this.arParamXSD = arParamXSD;
    }

    @Override
	public String toString () {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(",modelName=").append(modelName);
		sb.append(",arParamXSD=").append(arParamXSD);
		return sb.toString();
	}
}
