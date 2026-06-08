## HLA xAPI Adapter Federate

An HLA federate capable of converting HLA Interactions into xAPI Statements and depositing them in a Learning Record Store.

### To Run

The development runtime uses the vendored Portico RTI implementation. Portico loads `./RTI.rid` by default, so run commands from the repository root or set `RTI_RID_FILE` to the RID file you want to use.

You will need to update `config/Simulation.config` with the appropriate federate information, and `config/fom.xml` (or a new FOM referenced from `Simulation.config`) with the federate's appropriate FOM. The current config should work with the HlaStarterKit Project from Pitch.

To build and run federate once configured:

```shell
make clean build
make run-rti # if you would like to run a Portico RTI, do this and then open a new terminal for the next line
make run-dev
```
