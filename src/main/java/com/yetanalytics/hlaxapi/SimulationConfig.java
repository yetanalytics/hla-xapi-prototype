/*
 * Copyright (C) 2012  Pitch Technologies
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
package com.yetanalytics.hlaxapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class SimulationConfig {

    private static final String LOCAL_SETTINGS_DESIGNATOR = "localSettingsDesignator";
    private static final String FEDERATION_NAME = "federationName";
    private static final String FEDERATE_NAME = "federateName";

    private static final String SCENARIO_DIR = "scenarioDir";
    private static final String FOM = "fom";

    private final String localSettingsDesignator;
    private final String federationName;
    private final String federateName;

    private final String scenarioDir;
    private final String fom;

    public SimulationConfig(String fileName) throws IOException {
        this(new File(fileName));
    }

    public SimulationConfig(File file) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(file));

        localSettingsDesignator = properties.getProperty(LOCAL_SETTINGS_DESIGNATOR, "");
        federationName = properties.getProperty(FEDERATION_NAME, "HLA Tutorial");
        federateName = properties.getProperty(FEDERATE_NAME, "xAPI Processor");

        scenarioDir = properties.getProperty(SCENARIO_DIR, ".");
        fom = properties.getProperty(FOM, "fom.xml");
    }

    // mainly for tests
    public SimulationConfig(String localSettingsDesignator, String federationName, String federateName,
            String scenarioDir, String fom) {
        this.localSettingsDesignator = localSettingsDesignator;
        this.federationName = federationName;
        this.federateName = federateName;
        this.scenarioDir = scenarioDir;
        this.fom = fom;
    }

    public String getLocalSettingsDesignator() {
        return localSettingsDesignator;
    }

    public String getFederationName() {
        return federationName;
    }

    public String getFederateName() {
        return federateName;
    }

    public String getScenarioDir() {
        return scenarioDir;
    }

    public String getFom() {
        return fom;
    }
}
