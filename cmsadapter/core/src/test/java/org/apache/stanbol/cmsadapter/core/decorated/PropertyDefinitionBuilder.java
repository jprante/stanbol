/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.cmsadapter.core.decorated;

import static org.apache.stanbol.cmsadapter.core.decorated.NamingHelper.LOCAL_NAME;
import static org.apache.stanbol.cmsadapter.core.decorated.NamingHelper.NAMESPACE;
import static org.apache.stanbol.cmsadapter.core.decorated.NamingHelper.UNIQUE_REF;

import org.apache.stanbol.cmsadapter.servicesapi.model.web.ObjectFactory;
import org.apache.stanbol.cmsadapter.servicesapi.model.web.PropertyDefinition;

public class PropertyDefinitionBuilder {
    private ObjectFactory of = new ObjectFactory();
    private PropertyDefinition instance = of.createPropertyDefinition();

    public PropertyDefinitionBuilder(String prefix) {
        instance.setUniqueRef(prefix + UNIQUE_REF);
        instance.setLocalname(prefix + LOCAL_NAME);
        instance.setNamespace(prefix + NAMESPACE);
    }

    public PropertyDefinition build() {
        return instance;
    }

}
