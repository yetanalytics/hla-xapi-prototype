.PHONY: clean build build-pitch run-dev run-dev-pitch run-rti vendor-portico

APP_JAR := target/hla-xapi-1.0-SNAPSHOT-jar-with-dependencies.jar
RTI_RID ?= RTI.rid
SIM_CONFIG ?= config/Simulation.config
PITCH_SIM_CONFIG ?= config/Simulation.pitch.config
PITCH_RTI_LIB ?= $(HOME)/prti1516e/lib/*

build:
	mvn package

build-pitch:
	mvn -Ppitch-rti package

clean:
	mvn clean

vendor-portico:
	./scripts/vendor-portico.sh

run-dev:
	java -jar $(APP_JAR) $(SIM_CONFIG)

run-dev-pitch:
	java -cp "$(APP_JAR):$(PITCH_RTI_LIB)" com.yetanalytics.hlaxapi.App $(PITCH_SIM_CONFIG)

run-rti:
	java -cp $(APP_JAR) org.portico2.rti.Main --rid $(RTI_RID) $(RTI_ARGS)
