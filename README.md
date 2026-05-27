## HLA xAPI Adapter Federate

An HLA federate capable of converting HLA Interactions into xAPI Statements and depositing them in a Learning Record Store.

### To Run

You need an install of the Pitch RTI. You will need to get the system directory of the library from that install in order to share libraries with this project. This project cannot be bundled with Pitch proprietary RTI lib.

You will need to update `config/Simulation.config` with the appropriate federate information, and `config/fom.xml` (or a new FOM referenced from `Simulation.config`) with the federate's appropriate FOM. The current config should work with the HlaStarterKit Project from Pitch.

To build and run federate once configured:

```
make clean build
make run-dev PRTI_LIB=~/prti1516e/lib/*
```
