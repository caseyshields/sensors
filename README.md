# sensors

Simulates a hierarchical network of sensors and routers tracking a target.

These events can be serialized to JSON, pushed to a couchDB, or played back over a websocket on an express server.

Most of the models are pretty crude and only used to produce simple test datasets. Similar to some of the tests in the [angles-only tracker](https://github.com/mas12498/tracker).

After running ```npm install```, print simulation output to standard out by running 
```
npm run json
```

To upload the simulation to a couch database;
```
npm run couch <database> <user>:<pass>
```
For this to work you need a couchdb instance running...

# Single CouchDB Container

I'm trying to learn how I can use docker for setting up my environments, so this is kind of a note space as I figure things out. Here's the [documentation for the Official CouchDb image](https://hub.docker.com/_/couchdb)

To run the image you'll need something like;
```
docker run -dp 5984:5984 `
-v ${PWD}/couchdb/data:/opt/couchdb/data `
-v ${PWD}/couchdb/config:/opt/couchdb/etc/local.d `
-e COUCHDB_USER=admin `
-e COUCHDB_PASSWORD=password `
-d couchdb:latest
```
The volumes are bind mounts, so we can persist the database's configuration and data.

The environment variables you only need the first time, The CouchDb image will add a 'docker.ini' file to the configuration folder with a salted hash of the credentials you supplied.

You will need those credentials to configure the CouchDb server. Here's a [manual on how to do that with fauxton](https://docs.couchdb.org/en/stable/setup/single-node.html)

**//TODO I should write a script that handles that part using the [Cluster Setup API](https://docs.couchdb.org/en/stable/setup/cluster.html#the-cluster-setup-api)**


# Application using Docker Compose

If it's the first run you'll have to edit docker-compose.yml by
 - uncommenting the environment variables and replacing them with the actual credentials you want
   - You'll want to remove this item after you're up and running so your credentials aren't floating around in plaintext.
 - adjust the external default port mapping of the couchdb instance.
   - you might remove it if no other apps(like fauxton) outside the container are going to use it.
   

To launch the node app and couch db together, use docker compose;
```
docker-compose up -d
```

To stop and remove all the containers;
```
docker-compose down
```

The node application's root directory is on a bind mount currently so you can use it for development.

# TODO

- figure out how to do production and development builds

- figure out how to properly handle credentials and secrets

- figure out a better way to load or enter simulation configuration

- write a small node web app for uploading and then reading the simulation data.