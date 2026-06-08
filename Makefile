.PHONY: clean build run-dev vendor-portico

build:
	mvn package

clean:
	mvn clean

vendor-portico:
	./scripts/vendor-portico.sh

run-dev:
	java -jar target/hla-xapi-1.0-SNAPSHOT-jar-with-dependencies.jar
