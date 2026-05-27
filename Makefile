.phony: clean, build, run-dev, install-rti

build:
	mvn package

clean:
	mvn clean

#Example `make run-dev PRTI_LIB=~/prti1516e/lib/*`
run-dev:
	java -cp "target/hla-xapi-1.0-SNAPSHOT.jar:$(PRTI_LIB)" com.yetanalytics.hlaxapi.App
