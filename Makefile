.PHONY: clean build run-dev run-rti vendor-portico

APP_JAR := target/hla-xapi-1.0-SNAPSHOT-jar-with-dependencies.jar
RTI_RID ?= RTI.rid

build:
	mvn package

clean:
	mvn clean

vendor-portico:
	./scripts/vendor-portico.sh

run-dev:
	java -jar $(APP_JAR)

run-rti:
	java -cp $(APP_JAR) org.portico2.rti.Main --rid $(RTI_RID) $(RTI_ARGS)
