# CouchDB Configuration

This folder will be bind mounted to the configuration folder of a CouchDB container in Docker. Here's the [documentation for the Official CouchDb image](https://hub.docker.com/_/couchdb) 

Before the couch instance is up and running you have to configure CouchDB using the admin account.
To first run the image you'll need something like;
```
docker run -dp 5984:5984 `
-v ${PWD}/couchdb/data:/opt/couchdb/data `
-v ${PWD}/couchdb/config:/opt/couchdb/etc/local.d `
-e COUCHDB_USER=admin `
-e COUCHDB_PASSWORD=password `
-d couchdb:latest
```
The volumes(-v) are bind mounts, so we can persist the database's configuration and data.

The environment variables(-e) you only need the first time, The CouchDb image will add a 'docker.ini' file to the configuration folder with a salted hash of the credentials you supplied.

You will need those credentials to configure the CouchDb server. Here's a [manual on how to do that with fauxton](https://docs.couchdb.org/en/stable/setup/single-node.html)

**TODO I should write a script that handles that part using the [Cluster Setup API](https://docs.couchdb.org/en/stable/setup/cluster.html#the-cluster-setup-api)**

**TODO when we figure out deployment settings copy the config document here-MINUS the admin credentials...**