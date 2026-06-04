.PHONY: clean build run-dev vendor-portico

build:
	mvn package

clean:
	mvn clean

vendor-portico:
	./scripts/vendor-portico.sh

#Example `make run-dev PRTI_LIB=~/prti1516e/lib/*`
run-dev:
	java -cp "target/hla-xapi-1.0-SNAPSHOT-jar-with-dependencies.jar:$(PRTI_LIB)" com.yetanalytics.hlaxapi.App
