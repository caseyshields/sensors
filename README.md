# sensors

Simulates a hierarchical network of sensors and routers tracking a target.

These events can be serialized to JSON, pushed to a couchDB, or played back over a websocket on an express server.

Most of the models are pretty crude and only used to produce simple test datasets. Similar to some of the tests in the [angles-only tracker](https://github.com/mas12498/tracker).

After running ```npm install```, print simulation output to standard out by running 
```
npm run printout
```

To upload the simulation to a couch database;
```
npm run upload <ip> <user>:<pass>
```

For this to work you need a couchdb instance running. If you are running the script outside docker you probably want to use something like ```127.0.0.1``` for ```<ip>```. If it is inside Docker you should use the name of the container running it, ```couchdb```.

# Application using Docker Compose

To deploy all of the application containers(currently just couchdb and the node script uploader) together we can use Docker Compose.

To launch the node app in detached mode use;
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

- figure out a better way to load or enter simulation configuration

- a serverless architecture isn't really an option. Make a vertx server so I can finish the proof of concept web app...