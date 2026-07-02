## HLA xAPI Adapter Federate

An HLA federate capable of converting HLA RTI events (interactions and object updates) into xAPI Statements and storing them in a Learning Record Store.

### Configuration

The Federate uses multiple sources of configuration including a Simulation.config file and a json config file for configuring resulting xAPI.

#### Simulation Config

To connect to an HLA Federation, you will want to update the `Simulation.config` files (or `Simulation.pitch.config` if running in pitch mode). You will update the following fields to reflect the Federation details and also point to the appropriate FOM:

```
localSettingsDesignator=
federationName=HlaFedereplSimulation
federateName=xAPI Adapter HLA Federate

fom=config/HlaFedereplFOM.xml
```

#### xAPI Config

*Section in progress...*

The sample `xapi-config.json` reflects the current state of compatible configuration options. `statementTriggers` is an array of "triggers" which fire on a specific interaction or object update from the RTI. The trigger contains criteria (not yet implemented) and also a statement template which allows for the generation and storage of a valid xAPI statement. The statement template contains "injection" syntax which allow the insertion of details from the tiggering RTI update or values from an object cache (in progress) to be used as values in the statement.

##### Criteria
*Section in progress...*

##### Injections
*Section in progress...*

##### LRS Configuration
The `lrs` section contains the details for connecting to a valid Learning Record Store (LRS), and will be used to initialize a client which will write the resulting statements from the Federate to that LRS.

Example:

```
...
"lrs": {
    "host": "http://localhost:8080/xapi",
    "key": "my_key",
    "secret": "my_secret",
    "batch": 50
}
```

### Vendoring Portico

`make lib` rebuilds and vendors only Portico's Java jar into this repository's local Maven file repository. Portico ships its own Ant wrapper at `codebase/ant`, so no separate Ant installation is required.

The vendoring script intentionally uses a Java-only Portico Ant build file. On Linux, Portico's full `build.xml` imports C++ profiles that are not needed for this project and can fail during Ant parsing with a duplicate `cpp.hla13.compile` target before any Java compilation begins.

### To Run

The build output does not bundle an RTI implementation. The development runtime targets add either the vendored Portico RTI jar or a local Pitch RTI install on the runtime classpath.

You will need to update `config/Simulation.config` with the appropriate federate information, and `config/fom.xml` (or a new FOM referenced from `Simulation.config`) with the federate's appropriate FOM. The current config should work with the HlaStarterKit Project from Pitch.

To build and run federate once configured:

```shell
make clean build
make run-rti # if you would like to run a Portico RTI, do this and then open a new terminal for the next line
make run-dev
```

To run against a Pitch RTI installation instead of Portico:

```shell
make clean build
make run-dev-pitch PITCH_RTI_LIB="/path/to/prti1516e/lib/*"
```

### Development Checks

Use Maven's `verify` lifecycle before opening a PR:

```shell
make verify
```

This runs unit tests, builds the package, and fails if linting or formatting checks do not pass. To run only
linting locally:

```shell
make lint
```

To update formatting locally:

```shell
make format
```

Project style is intentionally minimal: Java uses 4-space indentation, no wildcard imports, braces on control
statements, UTF-8 text, LF line endings, final newlines, and no trailing whitespace. Make recipes keep required tab
indentation.

## License

Distributed under the Apache License version 2.0.

Copyright © 2026 Yet Analytics, Inc.
