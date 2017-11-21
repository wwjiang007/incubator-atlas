/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.impexp;

import com.google.inject.Inject;
import org.apache.atlas.RequestContextV1;
import org.apache.atlas.TestModules;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.impexp.AtlasImportRequest;
import org.apache.atlas.repository.store.graph.AtlasEntityStore;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Guice(modules = TestModules.TestOnlyModule.class)
public class ImportServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(ImportServiceTest.class);

    @Inject
    AtlasTypeRegistry typeRegistry;

    @Inject
    private AtlasTypeDefStore typeDefStore;

    @Inject
    private AtlasEntityStore entityStore;

    @DataProvider(name = "sales")
    public static Object[][] getDataFromQuickStart_v1_Sales(ITestContext context) throws IOException {
        return getZipSource("sales-v1-full.zip");
    }

    @Test(dataProvider = "sales")
    public void importDB1(ZipSource zipSource) throws AtlasBaseException, IOException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
        runAndVerifyQuickStart_v1_Import(new ImportService(typeDefStore, entityStore, typeRegistry), zipSource);
    }

    @DataProvider(name = "reporting")
    public static Object[][] getDataFromReporting() throws IOException {
        return getZipSource("reporting-v1-full.zip");
    }

    @Test(dataProvider = "reporting")
    public void importDB2(ZipSource zipSource) throws AtlasBaseException, IOException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
        runAndVerifyQuickStart_v1_Import(new ImportService(typeDefStore, entityStore, typeRegistry), zipSource);
    }

    @DataProvider(name = "logging")
    public static Object[][] getDataFromLogging(ITestContext context) throws IOException {
        return getZipSource("logging-v1-full.zip");
    }

    @Test(dataProvider = "logging")
    public void importDB3(ZipSource zipSource) throws AtlasBaseException, IOException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
        runAndVerifyQuickStart_v1_Import(new ImportService(typeDefStore, entityStore, typeRegistry), zipSource);
    }

    @DataProvider(name = "salesNewTypeAttrs")
    public static Object[][] getDataFromSalesNewTypeAttrs(ITestContext context) throws IOException {
        return getZipSource("salesNewTypeAttrs.zip");
    }

    @Test(dataProvider = "salesNewTypeAttrs", dependsOnMethods = "importDB1")
    public void importDB4(ZipSource zipSource) throws AtlasBaseException, IOException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
        runImportWithParameters(new ImportService(typeDefStore, entityStore, typeRegistry), getDefaultImportRequest(), zipSource);
    }

    @DataProvider(name = "salesNewTypeAttrs-next")
    public static Object[][] getDataFromSalesNewTypeAttrsNext(ITestContext context) throws IOException {
        return getZipSource("salesNewTypeAttrs-next.zip");
    }


    @Test(dataProvider = "salesNewTypeAttrs-next", dependsOnMethods = "importDB4")
    public void importDB5(ZipSource zipSource) throws AtlasBaseException, IOException {
        final String newEnumDefName = "database_action";

        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));

        AtlasImportRequest request = getDefaultImportRequest();
        Map<String, String> options = new HashMap<>();
        options.put("updateTypeDefinition", "false");
        request.setOptions(options);

        runImportWithParameters(new ImportService(typeDefStore, entityStore, typeRegistry), request, zipSource);
        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));
        assertEquals(typeDefStore.getEnumDefByName(newEnumDefName).getElementDefs().size(), 4);
    }

    @Test(dataProvider = "salesNewTypeAttrs-next", dependsOnMethods = "importDB4")
    public void importDB6(ZipSource zipSource) throws AtlasBaseException, IOException {
        final String newEnumDefName = "database_action";

        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));

        AtlasImportRequest request = getDefaultImportRequest();
        Map<String, String> options = new HashMap<>();
        options.put("updateTypeDefinition", "true");
        request.setOptions(options);

        runImportWithParameters(new ImportService(typeDefStore, entityStore, typeRegistry), request, zipSource);
        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));
        assertEquals(typeDefStore.getEnumDefByName(newEnumDefName).getElementDefs().size(), 8);
    }

    @DataProvider(name = "ctas")
    public static Object[][] getDataFromCtas(ITestContext context) throws IOException {
        return getZipSource("ctas.zip");
    }

    @Test(dataProvider = "ctas")
    public void importCTAS(ZipSource zipSource) throws IOException, AtlasBaseException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
        loadModelFromJson("0030-hive_model.json", typeDefStore, typeRegistry);

        AtlasImportRequest request = getDefaultImportRequest();
        runImportWithParameters(getImportService(), getDefaultImportRequest(), zipSource);
    }

    private ImportService getImportService() {
        return new ImportService(typeDefStore, entityStore, typeRegistry);
    }
}
