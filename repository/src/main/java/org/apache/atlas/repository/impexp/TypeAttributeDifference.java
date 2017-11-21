/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.impexp;

import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.impexp.AtlasImportResult;
import org.apache.atlas.model.typedef.AtlasClassificationDef;
import org.apache.atlas.model.typedef.AtlasEntityDef;
import org.apache.atlas.model.typedef.AtlasEnumDef;
import org.apache.atlas.model.typedef.AtlasStructDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.type.AtlasTypeRegistry;

import java.util.ArrayList;
import java.util.List;

public class TypeAttributeDifference {
    private final AtlasTypeDefStore typeDefStore;
    private final AtlasTypeRegistry typeRegistry;

    public TypeAttributeDifference(AtlasTypeDefStore typeDefStore, AtlasTypeRegistry typeRegistry) {
        this.typeDefStore = typeDefStore;
        this.typeRegistry = typeRegistry;
    }

    public void updateTypes(AtlasTypesDef typeDefinitionMap, AtlasImportResult result) throws AtlasBaseException {

        updateEntityDef(typeDefinitionMap, result);
        updateClassificationDef(typeDefinitionMap, result);
        updateEnumDef(typeDefinitionMap, result);
        updateStructDef(typeDefinitionMap, result);
    }

    private void updateEntityDef(AtlasTypesDef typeDefinitionMap, AtlasImportResult result) throws AtlasBaseException {
        for (AtlasEntityDef def: typeDefinitionMap.getEntityDefs()) {
            AtlasEntityDef existing = typeRegistry.getEntityDefByName(def.getName());
            if(existing != null && addAttributes(existing, def)) {
                typeDefStore.updateEntityDefByName(existing.getName(), existing);
                result.incrementMeticsCounter("typedef:entitydef:update");
            }
        }
    }

    private void updateClassificationDef(AtlasTypesDef typeDefinitionMap, AtlasImportResult result) throws AtlasBaseException {
        for (AtlasClassificationDef def: typeDefinitionMap.getClassificationDefs()) {
            AtlasClassificationDef existing = typeRegistry.getClassificationDefByName(def.getName());
            if(existing != null && addAttributes(existing, def)) {
                typeDefStore.updateClassificationDefByName(existing.getName(), existing);
                result.incrementMeticsCounter("typedef:classification:update");
            }
        }
    }

    private void updateEnumDef(AtlasTypesDef typeDefinitionMap, AtlasImportResult result) throws AtlasBaseException {
        for (AtlasEnumDef def: typeDefinitionMap.getEnumDefs()) {
            AtlasEnumDef existing = typeRegistry.getEnumDefByName(def.getName());
            if(existing != null && addElements(existing, def)) {
                typeDefStore.updateEnumDefByName(existing.getName(), existing);
                result.incrementMeticsCounter("typedef:enum:update");
            }
        }
    }

    private void updateStructDef(AtlasTypesDef typeDefinitionMap, AtlasImportResult result) throws AtlasBaseException {
        for (AtlasStructDef def: typeDefinitionMap.getStructDefs()) {
            AtlasStructDef existing = typeRegistry.getStructDefByName(def.getName());
            if(existing != null && addAttributes(existing, def)) {
                typeDefStore.updateStructDefByName(existing.getName(), existing);
                result.incrementMeticsCounter("typedef:struct:update");
            }
        }
    }

    private boolean addElements(AtlasEnumDef existing, AtlasEnumDef incoming) {
        return addElements(existing, getElementsAbsentInExisting(existing, incoming));
    }

    private boolean addAttributes(AtlasStructDef existing, AtlasStructDef incoming) {
        return addAttributes(existing, getElementsAbsentInExisting(existing, incoming));
    }

    private List<AtlasStructDef.AtlasAttributeDef> getElementsAbsentInExisting(AtlasStructDef existing, AtlasStructDef incoming) {
        List<AtlasStructDef.AtlasAttributeDef> difference = new ArrayList<>();
        for (AtlasStructDef.AtlasAttributeDef attr : incoming.getAttributeDefs()) {
            if(existing.getAttribute(attr.getName()) == null) {
                difference.add(attr);
            }
        }

        return difference;
    }

    private List<AtlasEnumDef.AtlasEnumElementDef> getElementsAbsentInExisting(AtlasEnumDef existing, AtlasEnumDef incoming) {
        List<AtlasEnumDef.AtlasEnumElementDef> difference = new ArrayList<>();
        for (AtlasEnumDef.AtlasEnumElementDef ed : incoming.getElementDefs()) {
            if(existing.getElement(ed.getValue()) == null) {
                difference.add(ed);
            }
        }

        return difference;
    }

    private boolean addAttributes(AtlasStructDef def, List<AtlasStructDef.AtlasAttributeDef> list) {
        for (AtlasStructDef.AtlasAttributeDef ad : list) {
            def.addAttribute(ad);
        }

        return list.size() > 0;
    }

    private boolean addElements(AtlasEnumDef def, List<AtlasEnumDef.AtlasEnumElementDef> list) {
        for (AtlasEnumDef.AtlasEnumElementDef ad : list) {
            def.addElement(ad);
        }

        return list.size() > 0;
    }
}
