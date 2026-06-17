## HLA xAPI Adapter Federate

An HLA federate capable of converting HLA Interactions into xAPI Statements and depositing them in a Learning Record Store.

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

This runs unit tests and builds the package. Formatting and linting are advisory in CI for now. To run them locally:

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
