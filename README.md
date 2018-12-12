# camunda-bpm-simulator

Camunda process engine plugin to simulate process execution.

## Purpose

When execution process definitions, the process engine waits from time to time for external events, for example user actions, conditions, timeouts etc.
This process engine plugin will generate these events to run processes without external interaction, hence, to simulate real world scenarios.

They way if and when these events are triggered and if and what payload data is to be generated is configured by [Camunda Properties](https://docs.camunda.org/manual/7.9/reference/bpmn20/custom-extensions/extension-elements/#properties) in the bpmn-files.
Optionally, all these properties can be set externally, for example in property files.

The plugin is able to simulate the past...

## How-To

Have a look at the example project, it is intended to be self explanatory.

## Properties for controlling

### None/Message/Signal/Conditional start event

* `simNextFire` : `<expression giving date>`
* `simInitBusinessKey` : `<expression giving string>`
* `simInitPayload` : `<varname>=<expression giving arbitrary value>`

### Message Receive events, Receive Task, Signal receive events

* `simNextFire` : `<expression giving date>`

Note for signals: The signal is not delivered globally but only to the execution that waits at the receive event.

### User Task

* `simNextFire` : `<expression giving date>`

TODO: Also simulate assignee, candidates, claiming...

### Service Task (Send Task, Business Rule Task, Script Task)

Replace behaviour by no-op, except following is set:

* `simCallRealImplementation` : `true`

### External Service Task

* `simNextFire` : `<expression giving date>`

### Business Rule Task

Replace behaviour by no-op, except DMN, this is "normally" called.

### All Flow Elements

#### Execution (Task) Listeners

Always stripped away, except the following is set:

* `simKeepListeners` : `true`

#### Properties for payload generation

* `simGeneratePayload` : `<varname>=<expression giving arbitrary value>`

## TODO

* think about throwing BPMN errors
* think about keeping execution/task listeners/
