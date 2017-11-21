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
package org.apache.atlas.repository.store.graph.v1;

import com.google.common.collect.ImmutableList;
import org.apache.atlas.TestModules;
import org.apache.atlas.model.instance.AtlasEntity;
import org.testng.annotations.Guice;

import static org.apache.atlas.type.AtlasTypeUtil.getAtlasObjectId;

/**
 * Inverse reference update test with {@link HardDeleteHandlerV1}
 */
@Guice(modules = TestModules.HardDeleteModule.class)
public class AtlasRelationshipStoreHardDeleteV1Test extends AtlasRelationshipStoreV1Test {

    @Override
    protected void verifyRelationshipAttributeUpdate_NonComposite_OneToMany(AtlasEntity jane) throws Exception {
        // Max should have been removed from the subordinates list, leaving only John.
        verifyRelationshipAttributeList(jane, "subordinates", ImmutableList.of(employeeNameIdMap.get("John")));
    }

    @Override
    protected void verifyRelationshipAttributeUpdate_NonComposite_ManyToOne(AtlasEntity a1, AtlasEntity a2,
                                                                            AtlasEntity a3, AtlasEntity b) {

        verifyRelationshipAttributeValue(a1, "oneB", null);

        verifyRelationshipAttributeValue(a2, "oneB", null);

        verifyRelationshipAttributeList(b, "manyA", ImmutableList.of(getAtlasObjectId(a3)));
    }

    @Override
    protected void verifyRelationshipAttributeUpdate_NonComposite_OneToOne(AtlasEntity a1, AtlasEntity b) {
        verifyRelationshipAttributeValue(a1, "b", null);
    }
}
