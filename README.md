# sensors

Simulates a network of targets, sensors, and routers, while generating a sequence of events.

These events are Js objects whose members often conform to D3 layout attributes.

These events can be serialized to JSON, pushed to a couchDB, or played back over a websocket on an express server.

Most of the models are pretty crude and only used to produce simple test datasets. Similar to some of the tests in the [angles-only tracker](https://github.com/mas12498/tracker).


### TODO

 - work out a proper interfaces rather than a bunch of one-off scripts
 - Might want to combine capabilities with the tracker project's test data generators as well...