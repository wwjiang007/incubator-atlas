/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.atlas.AtlasClient;
import org.apache.atlas.AtlasException;
import org.apache.atlas.EntityAuditEvent;
import org.apache.atlas.TestModules;
import org.apache.atlas.RequestContext;
import org.apache.atlas.TestUtils;
import org.apache.atlas.discovery.graph.GraphBackedDiscoveryService;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.listener.ChangedTypeDefs;
import org.apache.atlas.listener.EntityChangeListener;
import org.apache.atlas.listener.TypeDefChangeListener;
import org.apache.atlas.model.legacy.EntityResult;
import org.apache.atlas.query.QueryParams;
import org.apache.atlas.repository.audit.EntityAuditRepository;
import org.apache.atlas.repository.audit.HBaseBasedAuditRepository;
import org.apache.atlas.repository.audit.HBaseTestUtils;
import org.apache.atlas.repository.graph.AtlasGraphProvider;
import org.apache.atlas.services.DefaultMetadataService;
import org.apache.atlas.services.MetadataService;
import org.apache.atlas.type.AtlasTypeUtil;
import org.apache.atlas.typesystem.IReferenceableInstance;
import org.apache.atlas.typesystem.IStruct;
import org.apache.atlas.typesystem.ITypedReferenceableInstance;
import org.apache.atlas.typesystem.Referenceable;
import org.apache.atlas.typesystem.Struct;
import org.apache.atlas.typesystem.TypesDef;
import org.apache.atlas.typesystem.exception.EntityNotFoundException;
import org.apache.atlas.typesystem.exception.TypeNotFoundException;
import org.apache.atlas.typesystem.json.InstanceSerialization;
import org.apache.atlas.typesystem.json.TypesSerialization;
import org.apache.atlas.typesystem.persistence.Id;
import org.apache.atlas.typesystem.types.AttributeDefinition;
import org.apache.atlas.typesystem.types.ClassType;
import org.apache.atlas.typesystem.types.DataTypes;
import org.apache.atlas.typesystem.types.EnumValue;
import org.apache.atlas.typesystem.types.HierarchicalTypeDefinition;
import org.apache.atlas.typesystem.types.Multiplicity;
import org.apache.atlas.typesystem.types.TypeSystem;
import org.apache.atlas.typesystem.types.ValueConversionException;
import org.apache.atlas.typesystem.types.cache.TypeCache;
import org.apache.atlas.typesystem.types.utils.TypesUtil;
import org.apache.atlas.utils.ParamChecker;
import org.apache.commons.lang.RandomStringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.atlas.TestUtils.*;
import static org.apache.atlas.typesystem.types.utils.TypesUtil.createClassTypeDef;
import static org.apache.atlas.typesystem.types.utils.TypesUtil.createOptionalAttrDef;
import static org.testng.Assert.*;

@Guice(modules = TestModules.TestOnlyModule.class)
public class DefaultMetadataServiceTest {
    @Inject
    private MetadataService metadataService;

    private TypeDefChangeListener typeDefChangeListener;

    @Inject
    private EntityAuditRepository auditRepository;

    @Inject
    private GraphBackedDiscoveryService discoveryService;

    private Referenceable db = createDBEntity();

    private Referenceable table;

    private Id tableId;

    private final String NAME = "name";


    @BeforeTest
    public void setUp() throws Exception {

        typeDefChangeListener = (DefaultMetadataService)metadataService;
        metadataService = TestUtils.addSessionCleanupWrapper(metadataService);

        if (auditRepository instanceof HBaseBasedAuditRepository) {
            HBaseTestUtils.startCluster();
            ((HBaseBasedAuditRepository) auditRepository).start();
        }
        TestUtils.resetRequestContext();
        RequestContext.get().setUser("testuser");

        TypesDef typesDef = TestUtils.defineHiveTypes();
        try {
            metadataService.getTypeDefinition(TestUtils.TABLE_TYPE);
        } catch (TypeNotFoundException e) {
            metadataService.createType(TypesSerialization.toJson(typesDef));
        }

        String dbGUid = TestUtils.createInstance(metadataService, db);
        table = createTableEntity(dbGUid);
        String tableGuid = TestUtils.createInstance(metadataService, table);
        String tableDefinitionJson =
                metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        table = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        tableId = new Id(tableGuid, 0, TestUtils.TABLE_TYPE);
    }

    @AfterTest
    public void shutdown() throws Exception {
        try {
            TypeSystem.getInstance().reset();

            if (auditRepository instanceof HBaseBasedAuditRepository) {
                ((HBaseBasedAuditRepository) auditRepository).stop();
                HBaseTestUtils.stopCluster();
            }
        }
        finally {
            AtlasGraphProvider.cleanup();
        }
    }
    private EntityResult updateInstance(Referenceable entity) throws Exception {
        RequestContext.createContext();
        ParamChecker.notNull(entity, "Entity");
        ParamChecker.notNull(entity.getId(), "Entity");
        String entityjson = InstanceSerialization.toJson(entity, true);
        JSONArray entitiesJson = new JSONArray();
        entitiesJson.put(entityjson);
        return metadataService.updateEntities(entitiesJson.toString()).getEntityResult();
    }

    @Test(expectedExceptions = TypeNotFoundException.class)
    public void testCreateEntityWithUnknownDatatype() throws Exception {
        Referenceable entity = new Referenceable("Unknown datatype");
        String dbName = RandomStringUtils.randomAlphanumeric(10);
        entity.set(NAME, dbName);
        entity.set("description", "us db");
        TestUtils.createInstance(metadataService, entity);
        Assert.fail(TypeNotFoundException.class.getSimpleName() + " was expected but none thrown.");
    }

    @Test
    public void testCreateEntityWithUniqueAttribute() throws Exception {
        //name is the unique attribute
        Referenceable entity = createDBEntity();
        String id = TestUtils.createInstance(metadataService, entity);
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.ENTITY_CREATE);

        //using the same name should succeed, but not create another entity
        String newId = TestUtils.createInstance(metadataService, entity);
        assertNull(newId);

        //Same entity, but different qualified name should succeed
        entity.set(NAME, TestUtils.randomString());
        newId = TestUtils.createInstance(metadataService, entity);
        Assert.assertNotEquals(newId, id);
    }

    @Test
    //Titan doesn't allow some reserved chars in property keys. Verify that atlas encodes these
    //See GraphHelper.encodePropertyKey()
    public void testSpecialCharacters() throws Exception {
        //Verify that type can be created with reserved characters in typename, attribute name
        String strAttrName = randomStrWithReservedChars();
        String arrayAttrName = randomStrWithReservedChars();
        String mapAttrName = randomStrWithReservedChars();
        HierarchicalTypeDefinition<ClassType> typeDefinition =
                createClassTypeDef("test_type_"+ RandomStringUtils.randomAlphanumeric(10), ImmutableSet.<String>of(),
                        createOptionalAttrDef(strAttrName, DataTypes.STRING_TYPE),
                        new AttributeDefinition(arrayAttrName, DataTypes.arrayTypeName(DataTypes.STRING_TYPE.getName()),
                                Multiplicity.OPTIONAL, false, null),
                        new AttributeDefinition(mapAttrName,
                                DataTypes.mapTypeName(DataTypes.STRING_TYPE.getName(), DataTypes.STRING_TYPE.getName()),
                                Multiplicity.OPTIONAL, false, null));
        metadataService.createType(TypesSerialization.toJson(typeDefinition, false));

        //verify that entity can be created with reserved characters in string value, array value and map key and value
        Referenceable entity = new Referenceable(typeDefinition.typeName);
        entity.set(strAttrName, randomStrWithReservedChars());
        entity.set(arrayAttrName, new ArrayList<String>() {{ add(randomStrWithReservedChars()); }});
        entity.set(mapAttrName, new HashMap<String, String>() {{
            put(randomStrWithReservedChars(), randomStrWithReservedChars());
        }});
        String id = createInstance(metadataService, entity);

        //Verify that get entity definition returns actual values with reserved characters
        Referenceable instance =
                InstanceSerialization.fromJsonReferenceable(metadataService.getEntityDefinitionJson(id), true);
        assertReferenceableEquals(instance, entity);

        //Verify that search with reserved characters works - for string attribute
        String query =
                String.format("`%s` where `%s` = '%s'", typeDefinition.typeName, strAttrName, entity.get(strAttrName));
        String responseJson = discoveryService.searchByDSL(query, new QueryParams(1, 0));
        JSONObject response = new JSONObject(responseJson);
        assertEquals(response.getJSONArray("rows").length(), 1);
    }

    //equals excluding the id
    private void assertReferenceableEquals(Referenceable actual, Referenceable expected) {
        List<String> traits = actual.getTraits();
        Map<String, IStruct> traitsMap = new HashMap<>();
        for (String trait : traits) {
            traitsMap.put(trait, actual.getTrait(trait));
        }

        Referenceable newActual = new Referenceable(expected.getId(), actual.getTypeName(), actual.getValuesMap(),
                traits, traitsMap);
        assertEquals(newActual, expected);
    }

    private String randomStrWithReservedChars() {
        return randomString() + "\"${}%";
    }

    @Test
    public void testAddDeleteTrait() throws Exception {
        Referenceable entity = createDBEntity();
        String id = TestUtils.createInstance(metadataService, entity);

        //add trait
        Struct tag = new Struct(TestUtils.PII);
        metadataService.addTrait(id, InstanceSerialization.toJson(tag, true));

        List<String> traits = metadataService.getTraitNames(id);
        assertEquals(traits.size(), 1);
        assertEquals(traits.get(0), PII);

        //getTrait
        IStruct traitDefinition = metadataService.getTraitDefinition(id, PII);
        Assert.assertNotNull(traitDefinition);
        assertEquals(traitDefinition.getValuesMap().size(), 0);

        //delete trait
        metadataService.deleteTrait(id, PII);
        traits = metadataService.getTraitNames(id);
        assertEquals(traits.size(), 0);

        //add trait again
        metadataService.addTrait(id, InstanceSerialization.toJson(tag, true));

        traits = metadataService.getTraitNames(id);
        assertEquals(traits.size(), 1);
        assertEquals(traits.get(0), PII);
    }

    @Test
    public void testEntityAudit() throws Exception {
        //create entity
        Referenceable entity = createDBEntity();
        String id = TestUtils.createInstance(metadataService, entity);
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.ENTITY_CREATE);

        Struct tag = new Struct(TestUtils.PII);
        metadataService.addTrait(id, InstanceSerialization.toJson(tag, true));
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.TAG_ADD);

        metadataService.deleteTrait(id, TestUtils.PII);
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.TAG_DELETE);

        metadataService.updateEntityAttributeByGuid(id, "description", "new description");
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.ENTITY_UPDATE);

        metadataService.deleteEntities(Arrays.asList(id));
        assertAuditEvents(id, EntityAuditEvent.EntityAuditAction.ENTITY_DELETE);
    }

    private EntityResult deleteEntities(String... guids) throws AtlasException {
        RequestContext.createContext();
        return metadataService.deleteEntities(Arrays.asList(guids));
    }

    private void assertAuditEvents(String id, EntityAuditEvent.EntityAuditAction expectedAction) throws Exception {
        List<EntityAuditEvent> events =
                auditRepository.listEvents(id, null, (short) 10);
        for (EntityAuditEvent event : events) {
            if (event.getAction() == expectedAction) {
                return;
            }
        }
        fail("Expected audit action " + expectedAction);
    }

    private void assertAuditEvents(String entityId, int numEvents) throws Exception {
        List<EntityAuditEvent> events = metadataService.getAuditEvents(entityId, null, (short) numEvents);
        assertNotNull(events);
        assertEquals(events.size(), numEvents);
    }

    @Test
    public void testCreateEntityWithUniqueAttributeWithReference() throws Exception {
        Referenceable db = createDBEntity();
        String dbId = TestUtils.createInstance(metadataService, db);

        //Assert that there is just 1 audit events and thats for entity create
        assertAuditEvents(dbId, 1);
        assertAuditEvents(dbId, EntityAuditEvent.EntityAuditAction.ENTITY_CREATE);

        Referenceable table = new Referenceable(TestUtils.TABLE_TYPE);
        table.set(NAME, TestUtils.randomString());
        table.set("description", "random table");
        table.set("type", "type");
        table.set("tableType", "MANAGED");
        table.set("database", new Id(dbId, 0, TestUtils.DATABASE_TYPE));
        table.set("databaseComposite", db);
        TestUtils.createInstance(metadataService, table);

        //table create should re-use the db instance created earlier
        String tableDefinitionJson =
                metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        Referenceable actualDb = (Referenceable) tableDefinition.get("databaseComposite");
        assertEquals(actualDb.getId().id, dbId);

        //Assert that as part table create, db is not created and audit event is not added to db
        assertAuditEvents(dbId, 1);
    }

    @Test
    public void testUpdateEntityByUniqueAttribute() throws Exception {
        final List<String> colNameList = ImmutableList.of("col1", "col2");
        Referenceable tableUpdated = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put("columnNames", colNameList);
        }});
        metadataService.updateEntityByUniqueAttribute(table.getTypeName(), NAME, (String) table.get(NAME),
                tableUpdated);

        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        List<String> actualColumns = (List) tableDefinition.get("columnNames");
        assertEquals(actualColumns, colNameList);
    }

    @Test
    public void testUpdateEntityWithMap() throws Exception {
        final Map<String, Struct> partsMap = new HashMap<>();
        partsMap.put("part0", new Struct(TestUtils.PARTITION_STRUCT_TYPE,
            new HashMap<String, Object>() {{
                put(NAME, "test");
            }}));

        table.set("partitionsMap", partsMap);

        updateInstance(table);
        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertTrue(partsMap.get("part0").equalsContents(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part0")));

        //update map - add a map key
        partsMap.put("part1", new Struct(TestUtils.PARTITION_STRUCT_TYPE,
            new HashMap<String, Object>() {{
                put(NAME, "test1");
            }}));
        table.set("partitionsMap", partsMap);

        updateInstance(table);
        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        assertEquals(((Map<String, Struct>)tableDefinition.get("partitionsMap")).size(), 2);
        Assert.assertTrue(partsMap.get("part1").equalsContents(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part1")));

        //update map - remove a key and add another key
        partsMap.remove("part0");
        partsMap.put("part2", new Struct(TestUtils.PARTITION_STRUCT_TYPE,
            new HashMap<String, Object>() {{
                put(NAME, "test2");
            }}));
        table.set("partitionsMap", partsMap);

        updateInstance(table);
        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        assertEquals(((Map<String, Struct>)tableDefinition.get("partitionsMap")).size(), 2);
        Assert.assertNull(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part0"));
        Assert.assertTrue(partsMap.get("part2").equalsContents(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part2")));

        //update struct value for existing map key
        Struct partition2 = partsMap.get("part2");
        partition2.set(NAME, "test2Updated");
        updateInstance(table);
        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        assertEquals(((Map<String, Struct>)tableDefinition.get("partitionsMap")).size(), 2);
        Assert.assertNull(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part0"));
        Assert.assertTrue(partsMap.get("part2").equalsContents(((Map<String, Struct>)tableDefinition.get("partitionsMap")).get("part2")));

        //Test map pointing to a class
        final Map<String, Referenceable> columnsMap = new HashMap<>();
        Referenceable col0Type = new Referenceable(TestUtils.COLUMN_TYPE,
            new HashMap<String, Object>() {{
                put(NAME, "test1");
                put("type", "string");
            }});

        columnsMap.put("col0", col0Type);

        Referenceable col1Type = new Referenceable(TestUtils.COLUMN_TYPE,
            new HashMap<String, Object>() {{
                put(NAME, "test2");
                put("type", "string");
            }});

        columnsMap.put("col1", col1Type);
        table.set(TestUtils.COLUMNS_MAP, columnsMap);
        updateInstance(table);
        verifyMapUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columnsMap, TestUtils.COLUMNS_MAP);

        //Swap elements
        columnsMap.clear();
        columnsMap.put("col0", col1Type);
        columnsMap.put("col1", col0Type);

        table.set(TestUtils.COLUMNS_MAP, columnsMap);
        updateInstance(table);
        verifyMapUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columnsMap, TestUtils.COLUMNS_MAP);

        //Drop the first key and change the class type as well to col0
        columnsMap.clear();
        columnsMap.put("col0", col0Type);

        table.set(TestUtils.COLUMNS_MAP, columnsMap);
        updateInstance(table);
        verifyMapUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columnsMap, TestUtils.COLUMNS_MAP);

        //Clear state
        table.setNull(TestUtils.COLUMNS_MAP);
        updateInstance(table);
        verifyMapUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), null, TestUtils.COLUMNS_MAP);
    }

    private void verifyMapUpdates(String typeName, String uniqAttrName, String uniqAttrValue,
                                  Map<String, Referenceable> expectedMap, String mapAttrName) throws AtlasException {
        String json =
            metadataService.getEntityDefinition(typeName, uniqAttrName, uniqAttrValue);
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(json, true);
        Map<String, Referenceable> actualMap = (Map<String, Referenceable>) tableDefinition.get(mapAttrName);

        if (expectedMap == null && actualMap != null) {
            //all are marked as deleted in case of soft delete
            for (String key : actualMap.keySet()) {
                assertEquals(actualMap.get(key).getId().state, Id.EntityState.DELETED);
            }
        } else if(expectedMap == null) {
            //hard delete case
            assertNull(actualMap);
        } else {
            assertTrue(actualMap.size() >= expectedMap.size());

            for (String key : expectedMap.keySet()) {
                assertTrue(actualMap.get(key).equalsContents(expectedMap.get(key)));
            }

            //rest of the keys are marked as deleted
            List<String> extraKeys = new ArrayList<>(actualMap.keySet());
            extraKeys.removeAll(expectedMap.keySet());
            for (String key : extraKeys) {
                assertEquals(actualMap.get(key).getId().getState(), Id.EntityState.DELETED);
            }
        }
    }

    @Test
    public void testUpdateEntityAddAndUpdateArrayAttr() throws Exception {
        //Update entity, add new array attribute
        //add array of primitives
        final List<String> colNameList = ImmutableList.of("col1", "col2");
        Referenceable tableUpdated = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put("columnNames", colNameList);
        }});
        metadataService.updateEntityPartialByGuid(tableId._getId(), tableUpdated);

        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        List<String> actualColumns = (List) tableDefinition.get("columnNames");
        assertEquals(actualColumns, colNameList);

        //update array of primitives
        final List<String> updatedColNameList = ImmutableList.of("col2", "col3");
        tableUpdated = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put("columnNames", updatedColNameList);
        }});
        metadataService.updateEntityPartialByGuid(tableId.getId()._getId(), tableUpdated);

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        actualColumns = (List) tableDefinition.get("columnNames");
        assertEquals(actualColumns, updatedColNameList);
    }

    private EntityResult updateEntityPartial(String guid, Referenceable entity) throws AtlasException {
        RequestContext.createContext();
        return metadataService.updateEntityPartialByGuid(guid, entity).getEntityResult();
    }

    @Test
    public void testUpdateEntityArrayOfClass() throws Exception {
        //test array of class with id
        final List<Referenceable> columns = new ArrayList<>();
        Map<String, Object> values = new HashMap<>();
        values.put(NAME, "col1");
        values.put("type", "type");
        Referenceable col1 = new Referenceable(TestUtils.COLUMN_TYPE, values);
        columns.add(col1);
        Referenceable tableUpdated = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put(COLUMNS_ATTR_NAME, columns);
        }});

        EntityResult entityResult = updateEntityPartial(tableId._getId(), tableUpdated);
        assertEquals(entityResult.getCreatedEntities().size(), 1);  //col1 created
        assertEquals(entityResult.getUpdateEntities().size(), 1);  //table updated
        assertEquals(entityResult.getUpdateEntities().get(0), tableId._getId());
        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //Partial update. Add col2 But also update col1
        Map<String, Object> valuesCol5 = new HashMap<>();
        valuesCol5.put(NAME, "col2");
        valuesCol5.put("type", "type");
        Referenceable col2 = new Referenceable(TestUtils.COLUMN_TYPE, valuesCol5);
        //update col1
        col1.set("type", "type1");
        columns.add(col2);

        tableUpdated = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put(COLUMNS_ATTR_NAME, columns);
        }});
        entityResult = updateEntityPartial(tableId._getId(), tableUpdated);
        assertEquals(entityResult.getCreatedEntities().size(), 1);  //col2 created
        assertEquals(entityResult.getUpdateEntities().size(), 2);  //table, col1 updated

        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //Complete update. Add  array elements - col3,col4
        Map<String, Object> values1 = new HashMap<>();
        values1.put(NAME, "col3");
        values1.put("type", "type");
        Referenceable col3 = new Referenceable(TestUtils.COLUMN_TYPE, values1);
        columns.add(col3);

        Map<String, Object> values2 = new HashMap<>();
        values2.put(NAME, "col4");
        values2.put("type", "type");
        Referenceable col4 = new Referenceable(TestUtils.COLUMN_TYPE, values2);
        columns.add(col4);

        table.set(COLUMNS_ATTR_NAME, columns);
        entityResult = updateInstance(table);
        assertEquals(entityResult.getCreatedEntities().size(), 2);  //col3, col4 created

        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //Swap elements
        columns.clear();
        columns.add(col4);
        columns.add(col3);

        table.set(COLUMNS_ATTR_NAME, columns);
        entityResult = updateInstance(table);
        assertEquals(entityResult.getDeletedEntities().size(), 2);  //col1, col2 are deleted
        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //drop a single column
        columns.clear();
        columns.add(col3);

        table.set(COLUMNS_ATTR_NAME, columns);
        entityResult = updateInstance(table);
        assertEquals(entityResult.getDeletedEntities().size(), 1);  //col4 deleted
        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //Remove a class reference/Id and insert another reference
        //Also covers isComposite case since columns is a composite
        values.clear();
        columns.clear();

        values.put(NAME, "col5");
        values.put("type", "type");
        Referenceable col5 = new Referenceable(TestUtils.COLUMN_TYPE, values);
        columns.add(col5);
        table.set(COLUMNS_ATTR_NAME, columns);
        entityResult = updateInstance(table);
        assertEquals(entityResult.getCreatedEntities().size(), 1);  //col5 created
        assertEquals(entityResult.getDeletedEntities().size(), 1);  //col3 deleted

        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), columns, COLUMNS_ATTR_NAME);

        //Update array column to null
        table.setNull(COLUMNS_ATTR_NAME);
        entityResult = updateInstance(table);
        assertEquals(entityResult.getDeletedEntities().size(), 1);
        verifyArrayUpdates(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME), null, COLUMNS_ATTR_NAME);
    }

    private void verifyArrayUpdates(String typeName, String uniqAttrName, String uniqAttrValue,
                                    List<Referenceable> expectedArray, String arrAttrName) throws AtlasException {
        String json = metadataService.getEntityDefinition(typeName, uniqAttrName, uniqAttrValue);
        Referenceable entityDefinition = InstanceSerialization.fromJsonReferenceable(json, true);
        List<Referenceable> actualArray = (List<Referenceable>) entityDefinition.get(arrAttrName);
        if (expectedArray == null && actualArray != null) {
            //all are marked as deleted in case of soft delete
            for (Referenceable referenceable : actualArray) {
                assertEquals(referenceable.getId().state, Id.EntityState.DELETED);
            }
        } else if(expectedArray == null) {
            //hard delete case
            assertNull(actualArray);
        } else {
            int index;
            for (index = 0; index < expectedArray.size(); index++) {
                Assert.assertTrue(actualArray.get(index).equalsContents(expectedArray.get(index)));
            }

            //Rest of the entities in the list are marked as deleted
            for (; index < actualArray.size(); index++) {
                assertEquals(actualArray.get(index).getId().state, Id.EntityState.DELETED);
            }
        }
    }

    @Test
    public void testStructs() throws Exception {
        Struct serdeInstance = new Struct(TestUtils.SERDE_TYPE);
        serdeInstance.set(NAME, "serde1Name");
        serdeInstance.set("serde", "test");
        serdeInstance.set("description", "testDesc");
        table.set("serde1", serdeInstance);

        String newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        Assert.assertNotNull(tableDefinition.get("serde1"));
        Assert.assertTrue(serdeInstance.equalsContents(tableDefinition.get("serde1")));

        //update struct attribute
        serdeInstance.set("serde", "testUpdated");
        updateInstance(table);
        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertTrue(serdeInstance.equalsContents(tableDefinition.get("serde1")));

        //set to null
        serdeInstance.setNull("description");
        updateInstance(table);
        tableDefinitionJson =
            metadataService.getEntityDefinitionJson(tableId._getId());
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        Assert.assertNull(((Struct) tableDefinition.get("serde1")).get("description"));
    }


    @Test
    public void testCreateEntityWithReferenceableHavingIdNoValue() throws Exception {
        //ATLAS-383 Test
        Referenceable sdReferenceable = new Referenceable(TestUtils.STORAGE_DESC_TYPE);
        sdReferenceable.set(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, TestUtils.randomString());
            sdReferenceable.set("compressed", "false");
            sdReferenceable.set("location", "hdfs://tmp/hive-user");
        String sdGuid = TestUtils.createInstance(metadataService, sdReferenceable);

        Referenceable sdRef2 = new Referenceable(sdGuid, TestUtils.STORAGE_DESC_TYPE, null);

        Referenceable partRef = new Referenceable(TestUtils.PARTITION_CLASS_TYPE);
        partRef.set(AtlasClient.REFERENCEABLE_ATTRIBUTE_NAME, "part-unique");
        partRef.set("values", ImmutableList.of("2014-10-01"));
        partRef.set("table", table);
        partRef.set("sd", sdRef2);

        String partGuid = TestUtils.createInstance(metadataService, partRef);
        Assert.assertNotNull(partGuid);
    }

    @Test
    public void testClassUpdate() throws Exception {
        //Create new db instance
        final Referenceable databaseInstance = new Referenceable(TestUtils.DATABASE_TYPE);
        databaseInstance.set(NAME, TestUtils.randomString());
        databaseInstance.set("description", "new database");

        String dbId = TestUtils.createInstance(metadataService, databaseInstance);

        /*Update reference property with Id */
        metadataService.updateEntityAttributeByGuid(tableId._getId(), "database", dbId);

        String tableDefinitionJson =
            metadataService.getEntityDefinitionJson(tableId._getId());
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        assertEquals(dbId, (((Id) tableDefinition.get("database"))._getId()));

        /* Update with referenceable - TODO - Fails . Need to fix this */
        /*final String dbName = TestUtils.randomString();
        final Referenceable databaseInstance2 = new Referenceable(TestUtils.DATABASE_TYPE);
        databaseInstance2.set(NAME, dbName);
        databaseInstance2.set("description", "new database 2");

        Referenceable updateTable = new Referenceable(TestUtils.TABLE_TYPE, new HashMap<String, Object>() {{
            put("database", databaseInstance2);
        }});
        metadataService.updateEntityAttributeByGuid(tableId._getId(), updateTable);

        tableDefinitionJson =
            metadataService.getEntityDefinition(tableId._getId());
        Referenceable tableDefinitionActual = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        String dbDefJson = metadataService.getEntityDefinition(TestUtils.DATABASE_TYPE, NAME, dbName);
        Referenceable dbDef = InstanceSerialization.fromJsonReferenceable(dbDefJson, true);

        Assert.assertNotEquals(dbId, (((Id) tableDefinitionActual.get("database"))._getId()));
        Assert.assertEquals(dbDef.getObjectId()._getId(), (((Id) tableDefinitionActual.get("database"))._getId())); */

    }

    @Test
    public void testArrayOfStructs() throws Exception {
        //Add array of structs
        TestUtils.dumpGraph(TestUtils.getGraph());

        final Struct partition1 = new Struct(TestUtils.PARTITION_STRUCT_TYPE);
        partition1.set(NAME, "part1");

        final Struct partition2 = new Struct(TestUtils.PARTITION_STRUCT_TYPE);
        partition2.set(NAME, "part2");

        List<Struct> partitions = new ArrayList<Struct>(){{ add(partition1); add(partition2); }};
        table.set("partitions", partitions);

        String newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNotNull(tableDefinition.get("partitions"));
        List<Struct> partitionsActual = (List<Struct>) tableDefinition.get("partitions");
        assertPartitions(partitionsActual, partitions);

        //add a new element to array of struct
        final Struct partition3 = new Struct(TestUtils.PARTITION_STRUCT_TYPE);
        partition3.set(NAME, "part3");
        partitions.add(partition3);
        table.set("partitions", partitions);
        newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNotNull(tableDefinition.get("partitions"));
        partitionsActual = (List<Struct>) tableDefinition.get("partitions");
        assertPartitions(partitionsActual, partitions);

        //remove one of the struct values
        partitions.remove(1);
        table.set("partitions", partitions);
        newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNotNull(tableDefinition.get("partitions"));
        partitionsActual = (List<Struct>) tableDefinition.get("partitions");
        assertPartitions(partitionsActual, partitions);

        //Update struct value within array of struct
        partitions.get(0).set(NAME, "part4");
        newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNotNull(tableDefinition.get("partitions"));
        partitionsActual = (List<Struct>) tableDefinition.get("partitions");
        assertPartitions(partitionsActual, partitions);

        //add a repeated element to array of struct
        final Struct partition4 = new Struct(TestUtils.PARTITION_STRUCT_TYPE);
        partition4.set(NAME, "part4");
        partitions.add(partition4);
        table.set("partitions", partitions);
        newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNotNull(tableDefinition.get("partitions"));
        partitionsActual = (List<Struct>) tableDefinition.get("partitions");
        assertPartitions(partitionsActual, partitions);


        // Remove all elements. Should set array attribute to null
        partitions.clear();
        newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        Assert.assertNull(tableDefinition.get("partitions"));
    }

    private void assertPartitions(List<Struct> partitionsActual, List<Struct> partitions) {
        assertEquals(partitionsActual.size(), partitions.size());
        for (int index = 0; index < partitions.size(); index++) {
            assertTrue(partitionsActual.get(index).equalsContents(partitions.get(index)));
        }
    }

    @Test(expectedExceptions = ValueConversionException.class)
    public void testCreateRequiredAttrNull() throws Exception {
        //Update required attribute

        Referenceable tableEntity = new Referenceable(TABLE_TYPE);
        tableEntity.set(NAME, "table_" + TestUtils.randomString());

        TestUtils.createInstance(metadataService, tableEntity);
        Assert.fail("Expected exception while creating with required attribute null");
    }

    @Test(expectedExceptions = ValueConversionException.class)
    public void testUpdateRequiredAttrToNull() throws Exception {
        //Update required attribute
        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        assertEquals(tableDefinition.get("description"), "random table");
        table.setNull("description");

        updateInstance(table);
        Assert.fail("Expected exception while updating required attribute to null");
    }

    @Test
    public void testCheckOptionalAttrValueRetention() throws Exception {

        Referenceable entity = createDBEntity();

        String dbId = TestUtils.createInstance(metadataService, entity);

        entity = getEntity(dbId);

        //The optional boolean attribute should have a non-null value
        final String isReplicatedAttr = "isReplicated";
        final String paramsAttr = "parameters";
        Assert.assertNotNull(entity.get(isReplicatedAttr));
        Assert.assertEquals(entity.get(isReplicatedAttr), Boolean.FALSE);
        Assert.assertNull(entity.get(paramsAttr));

        //Update to true
        entity.set(isReplicatedAttr, Boolean.TRUE);
        //Update array
        final HashMap<String, String> params = new HashMap<String, String>() {{ put("param1", "val1"); put("param2", "val2"); }};
        entity.set(paramsAttr, params);
        //Complete update
        updateInstance(entity);

        entity = getEntity(dbId);

        Assert.assertNotNull(entity.get(isReplicatedAttr));
        Assert.assertEquals(entity.get(isReplicatedAttr), Boolean.TRUE);
        Assert.assertEquals(entity.get(paramsAttr), params);

        //Complete update without setting the attribute
        Referenceable newEntity = createDBEntity();
        //Reset name to the current DB name
        newEntity.set(NAME, entity.get(NAME));
        updateInstance(newEntity);

        entity = getEntity(dbId);
        Assert.assertNotNull(entity.get(isReplicatedAttr));
        Assert.assertEquals(entity.get(isReplicatedAttr), Boolean.TRUE);
        Assert.assertEquals(entity.get(paramsAttr), params);
    }

    private Referenceable getEntity(String guid) throws AtlasException {
        String entityJson = metadataService.getEntityDefinitionJson(guid);
        Assert.assertNotNull(entityJson);
        return InstanceSerialization.fromJsonReferenceable(entityJson, true);
    }

    @Test
    public void testUpdateOptionalAttrToNull() throws Exception {
        String tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);

        //Update optional Attribute
        Assert.assertNotNull(tableDefinition.get("created"));
        //Update optional attribute
        table.setNull("created");

        String newtableId = updateInstance(table).getUpdateEntities().get(0);
        assertEquals(newtableId, tableId._getId());

        tableDefinitionJson =
            metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        Assert.assertNull(tableDefinition.get("created"));
    }

    @Test
    public void testCreateEntityWithEnum ()throws Exception {
        String tableDefinitionJson =
                metadataService.getEntityDefinition(TestUtils.TABLE_TYPE, NAME, (String) table.get(NAME));
        Referenceable tableDefinition = InstanceSerialization.fromJsonReferenceable(tableDefinitionJson, true);
        EnumValue tableType = (EnumValue) tableDefinition.get("tableType");

        assertEquals(tableType, new EnumValue("MANAGED", 1));
    }

    @Test
    public void testGetEntityByUniqueAttribute() throws Exception {
        Referenceable entity = createDBEntity();
        TestUtils.createInstance(metadataService, entity);

        //get entity by valid qualified name
        String entityJson = metadataService.getEntityDefinition(TestUtils.DATABASE_TYPE, NAME,
                (String) entity.get(NAME));
        Assert.assertNotNull(entityJson);
        Referenceable referenceable = InstanceSerialization.fromJsonReferenceable(entityJson, true);
        assertEquals(referenceable.get(NAME), entity.get(NAME));

        //get entity by invalid qualified name
        try {
            metadataService.getEntityDefinition(TestUtils.DATABASE_TYPE, NAME, "random");
            Assert.fail("Expected EntityNotFoundException");
        } catch (EntityNotFoundException e) {
            //expected
        }

        //get entity by non-unique attribute
        try {
            metadataService.getEntityDefinition(TestUtils.DATABASE_TYPE, "description",
                    (String) entity.get("description"));
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            //expected
        }
    }

    @Test
    public void testDeleteEntities() throws Exception {
        // Create a table entity, with 3 composite column entities
        Referenceable dbEntity = createDBEntity();
        String dbGuid = TestUtils.createInstance(metadataService, dbEntity);
        Referenceable table1Entity = createTableEntity(dbGuid);
        Referenceable col1 = createColumnEntity();
        Referenceable col2 = createColumnEntity();
        Referenceable col3 = createColumnEntity();
        table1Entity.set(COLUMNS_ATTR_NAME, ImmutableList.of(col1, col2, col3));
        TestUtils.createInstance(metadataService, table1Entity);

        // Retrieve the table entities from the repository,
        // to get their guids and the composite column guids.
        String entityJson = metadataService.getEntityDefinition(TestUtils.TABLE_TYPE,
                NAME, (String)table1Entity.get(NAME));
        Assert.assertNotNull(entityJson);
        table1Entity = InstanceSerialization.fromJsonReferenceable(entityJson, true);
        List<IReferenceableInstance> table1Columns = (List<IReferenceableInstance>) table1Entity.get(COLUMNS_ATTR_NAME);

        // Register an EntityChangeListener to verify the notification mechanism
        // is working for deleteEntities().
        EntitiesChangeListener listener = new EntitiesChangeListener();
        metadataService.registerListener(listener);

        //Delete one column
        String columnId = table1Columns.get(0).getId()._getId();
        EntityResult entityResult = deleteEntities(columnId);
        //column is deleted and table is updated
        assertEquals(entityResult.getDeletedEntities().get(0), columnId);
        assertEquals(entityResult.getUpdateEntities().get(0), table1Entity.getId()._getId());

        //verify listener was called for updates and deletes
        assertEquals(entityResult.getDeletedEntities(), listener.getDeletedEntities());
        assertEquals(entityResult.getUpdateEntities(), listener.getUpdatedEntities());

        // Delete the table entities.  The deletion should cascade
        // to their composite columns.
        entityResult = deleteEntities(table1Entity.getId()._getId());

        // Verify that deleteEntities() response has guids for tables and their composite columns.
        Assert.assertTrue(entityResult.getDeletedEntities().contains(table1Entity.getId()._getId()));
        Assert.assertTrue(entityResult.getDeletedEntities().contains(table1Columns.get(1).getId()._getId()));
        Assert.assertTrue(entityResult.getDeletedEntities().contains(table1Columns.get(2).getId()._getId()));

        // Verify that tables and their composite columns have been deleted from the repository.
        assertEntityDeleted(TABLE_TYPE, NAME, table1Entity.get(NAME));
        assertEntityDeleted(COLUMN_TYPE, NAME, col2.get(NAME));
        assertEntityDeleted(COLUMN_TYPE, NAME, col3.get(NAME));

        // Verify that the listener was notified about the deleted entities.
        List<String> deletedEntitiesFromListener = listener.getDeletedEntities();
        Assert.assertNotNull(deletedEntitiesFromListener);
        assertEquals(deletedEntitiesFromListener.size(), entityResult.getDeletedEntities().size());
        Assert.assertTrue(deletedEntitiesFromListener.containsAll(entityResult.getDeletedEntities()));
    }

    private void assertEntityDeleted(String typeName, String attributeName, Object attributeValue)
            throws AtlasException {
        try {
            metadataService.getEntityDefinition(typeName, attributeName, (String) attributeValue);
            fail("Expected EntityNotFoundException");
        } catch(EntityNotFoundException e) {
            //expected
        }
    }

    @Test
    public void testDeleteEntityByUniqueAttribute() throws Exception {
        // Create a table entity, with 3 composite column entities
        Referenceable dbEntity = createDBEntity();
        String dbGuid = TestUtils.createInstance(metadataService, dbEntity);
        Referenceable table1Entity = createTableEntity(dbGuid);
        Referenceable col1 = createColumnEntity();
        Referenceable col2 = createColumnEntity();
        Referenceable col3 = createColumnEntity();
        table1Entity.set(COLUMNS_ATTR_NAME, ImmutableList.of(col1, col2, col3));
        TestUtils.createInstance(metadataService, table1Entity);

        // to get their guids and the composite column guids.
        String entityJson = metadataService.getEntityDefinition(TestUtils.TABLE_TYPE,
                NAME, (String) table1Entity.get(NAME));
        Assert.assertNotNull(entityJson);
        table1Entity = InstanceSerialization.fromJsonReferenceable(entityJson, true);
        List<IReferenceableInstance> table1Columns = (List<IReferenceableInstance>) table1Entity.get(COLUMNS_ATTR_NAME);

        // Register an EntityChangeListener to verify the notification mechanism
        // is working for deleteEntityByUniqueAttribute().
        EntitiesChangeListener listener = new EntitiesChangeListener();
        metadataService.registerListener(listener);

        // Delete the table entities.  The deletion should cascade
        // to their composite columns.
        List<String> deletedGuids = metadataService.deleteEntityByUniqueAttribute(TestUtils.TABLE_TYPE, NAME,
                (String) table1Entity.get(NAME)).getDeletedEntities();

        // Verify that deleteEntities() response has guids for tables and their composite columns.
        Assert.assertTrue(deletedGuids.contains(table1Entity.getId()._getId()));
        for (IReferenceableInstance column : table1Columns) {
            Assert.assertTrue(deletedGuids.contains(column.getId()._getId()));
        }

        // Verify that tables and their composite columns have been deleted from the repository.
        // Verify that tables and their composite columns have been deleted from the repository.
        assertEntityDeleted(TABLE_TYPE, NAME, table1Entity.get(NAME));
        assertEntityDeleted(COLUMN_TYPE, NAME, col1.get(NAME));
        assertEntityDeleted(COLUMN_TYPE, NAME, col2.get(NAME));
        assertEntityDeleted(COLUMN_TYPE, NAME, col3.get(NAME));

        // Verify that the listener was notified about the deleted entities.
        List<String> deletedEntitiesFromListener = listener.getDeletedEntities();
        Assert.assertNotNull(deletedEntitiesFromListener);
        assertEquals(deletedEntitiesFromListener.size(), deletedGuids.size());
        Assert.assertTrue(deletedEntitiesFromListener.containsAll(deletedGuids));
    }

    @Test
    public void testTypeUpdateFailureShouldRollBack() throws AtlasException, JSONException {
        String typeName = "test_type_"+ RandomStringUtils.randomAlphanumeric(10);
        HierarchicalTypeDefinition<ClassType> typeDef = TypesUtil.createClassTypeDef(
                typeName, ImmutableSet.<String>of(),
                TypesUtil.createUniqueRequiredAttrDef("test_type_attribute", DataTypes.STRING_TYPE));
        TypesDef typesDef = new TypesDef(typeDef, false);
        JSONObject type = metadataService.createType(TypesSerialization.toJson(typesDef));
        Assert.assertNotNull(type.get(AtlasClient.TYPES));

        HierarchicalTypeDefinition<ClassType> updatedTypeDef = TypesUtil.createClassTypeDef(
            typeName, ImmutableSet.<String>of(),
            TypesUtil.createUniqueRequiredAttrDef("test_type_attribute", DataTypes.STRING_TYPE),
            TypesUtil.createRequiredAttrDef("test_type_invalid_attribute$", DataTypes.STRING_TYPE));
        TypesDef updatedTypesDef = new TypesDef(updatedTypeDef, false);

        try {
            metadataService.updateType(TypesSerialization.toJson(updatedTypesDef));
            fail("Expected AtlasException");
        } catch (AtlasException e) {
            //expected
        }

        //type definition should reflect old type
        String typeDefinition = metadataService.getTypeDefinition(typeName);
        typesDef = TypesSerialization.fromJson(typeDefinition);
        assertEquals(typesDef.classTypes().head().attributeDefinitions.length, 1);
    }

    @Test
    public void testTypeWithDotsCreationShouldNotBeCreated() throws AtlasException, JSONException {
        String typeName = "test_.v1_type_XXXX";
        HierarchicalTypeDefinition<ClassType> typeDef = TypesUtil.createClassTypeDef(
                typeName, ImmutableSet.<String>of(),
                TypesUtil.createUniqueRequiredAttrDef("test_type_attribute", DataTypes.STRING_TYPE));
        TypesDef typesDef = new TypesDef(typeDef, false);

        try {
            metadataService.createType(TypesSerialization.toJson(typesDef));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue (e.getCause().getMessage().contains(AtlasTypeUtil.getInvalidTypeNameErrorMessage()), e.getCause().getMessage());
        }
    }

    @Test
    public void testAuditEventsInvalidParams() throws Exception {
        //entity id can't be null
        try {
            metadataService.getAuditEvents(null, "key", (short) 10);
            fail("expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            //expected IllegalArgumentException
            assertEquals(e.getMessage(), "entity id cannot be null");
        }

        //entity id can't be empty
            try {
            metadataService.getAuditEvents("", "key", (short) 10);
            fail("expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            //expected IllegalArgumentException
            assertEquals(e.getMessage(), "entity id cannot be empty");
        }

        //start key can be null
        metadataService.getAuditEvents("id", null, (short) 10);

        //start key can't be emoty
        try {
            metadataService.getAuditEvents("id", "", (short) 10);
            fail("expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            //expected IllegalArgumentException
            assertEquals(e.getMessage(), "start key cannot be empty");
        }

        //number of results can't be > max value
        try {
            metadataService.getAuditEvents("id", "key", (short) 10000);
            fail("expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            //expected IllegalArgumentException
            assertEquals(e.getMessage(), "count should be <= 1000, current value 10000");
        }

        //number of results can't be <= 0
        try {
            metadataService.getAuditEvents("id", "key", (short) -1);
            fail("expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {
            //expected IllegalArgumentException
            assertEquals(e.getMessage(), "count should be > 0, current value -1");
        }
    }

    @Test
    public void testOnChangeRefresh() {
        try {
            List<String> beforeChangeTypeNames = new ArrayList<>();
            beforeChangeTypeNames.addAll(metadataService.getTypeNames(new HashMap<TypeCache.TYPE_FILTER, String>()));

            typeDefChangeListener.onChange(new ChangedTypeDefs());

            List<String> afterChangeTypeNames = new ArrayList<>();
            afterChangeTypeNames.addAll(metadataService.getTypeNames(new HashMap<TypeCache.TYPE_FILTER, String>()));

            Collections.sort(beforeChangeTypeNames);
            Collections.sort(afterChangeTypeNames);
            assertEquals(afterChangeTypeNames, beforeChangeTypeNames);
        } catch (AtlasBaseException e) {
            fail("Should've succeeded", e);
        } catch (AtlasException e) {
            fail("getTypeNames should've succeeded", e);
        }
    }

    private static class EntitiesChangeListener implements EntityChangeListener {
        private List<String> deletedEntities = new ArrayList<>();
        private List<String> updatedEntities = new ArrayList<>();

        @Override
        public void onEntitiesAdded(Collection<ITypedReferenceableInstance> entities, boolean isImport)
            throws AtlasException {
        }

        @Override
        public void onEntitiesUpdated(Collection<ITypedReferenceableInstance> entities, boolean isImport)
            throws AtlasException {
            updatedEntities.clear();
            for (ITypedReferenceableInstance entity : entities) {
                updatedEntities.add(entity.getId()._getId());
            }
        }

        @Override
        public void onTraitsAdded(ITypedReferenceableInstance entity, Collection<? extends IStruct> traits)
            throws AtlasException {
        }

        @Override
        public void onTraitsDeleted(ITypedReferenceableInstance entity, Collection<String> traitNames)
            throws AtlasException {
        }

        @Override
        public void onTraitsUpdated(ITypedReferenceableInstance entity, Collection<? extends IStruct> traits)
            throws AtlasException {
        }

        @Override
        public void onEntitiesDeleted(Collection<ITypedReferenceableInstance> entities, boolean isImport)
            throws AtlasException {
            deletedEntities.clear();
            for (ITypedReferenceableInstance entity : entities) {
                deletedEntities.add(entity.getId()._getId());
            }
        }

        public List<String> getDeletedEntities() {
            return deletedEntities;
        }

        public List<String> getUpdatedEntities() {
            return updatedEntities;
        }
    }
}
