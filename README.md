# sensors

Simulates a network of targets, sensors, and routers, while generating a sequence of events.

These events are Js objects whose members often conform to D3 layout attributes.

These events can be serialized to JSON, pushed to a couchDB, or played back over a websocket on an express server.

Most of the models are pretty crude and only used to produce simple test datasets. Similar to some of the tests in the [angles-only tracker](https://github.com/mas12498/tracker).

# CouchDB Container

```
docker build -t my-couchdb .
docker run -dp 5984:5984 my-couchdb
```

you can provide env vars for the admin credentials, and the image will create the ini file with hashed and salted keys
you have to bind mount the data dir for persistence, and map the config dir to get the ini file

```
docker run -dp 5984:5984 `
-v ${PWD}/couchdb/data:/opt/couchdb/data `
-v ${PWD}/couchdb/config:/opt/couchdb/etc/local.d `
-e COUCHDB_USER=admin `
-e COUCHDB_PASSWORD=password `
-d couchdb:latest
```

but you don't normally want to start the container this way, so once you have the ini, instead use

```
docker run -dp 5984:5984 `
-v ${PWD}/couchdb/data:/opt/couchdb/data `
-v ${PWD}/couchdb/config:/opt/couchdb/etc/local.d `
-d couchdb:latest
```

# Docker compose

```
docker-compose 
```

### TODO

 - work out a proper interfaces rather than a bunch of one-off scripts
 - Might want to combine capabilities with the tracker project's test data generators as well...