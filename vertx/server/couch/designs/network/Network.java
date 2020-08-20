package server.couch.designs.network;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/** Provides a CouchDB Design Document for the Network visualization data product.
 * It has one default view which is all events in chronological order.
 * The key is constructed from the events' timestamps and sources. */
public class Network {

    /** this should be the name provided to the design document in the mission database */
    public static final String DESIGN_NAME = "network";

    /** Provide a chronological view of network events in [time, source] order */
    public static final String DefaultView = "events";

    /** This script contains a map function for the default event view */
    static final String EVENT_SCRIPT = "vertx\\server\\couch\\designs\\network\\events.map.js";

    public String getName() { return DESIGN_NAME; }

    public Future<JsonObject> getDesignDocument() {
        Vertx vertx = Vertx.currentContext().owner();
        Promise<JsonObject> promise = Promise.promise();

        vertx.fileSystem().readFile( EVENT_SCRIPT, read -> {

            // check for file problems
            if (!read.succeeded())
                promise.fail(read.cause());
            String script = read.result().toString();
            if (script==null || script.length()==0)
                promise.fail("Error: map script missing.");

            // create a design document object for the CouchDB API
            JsonObject design = new JsonObject()
                    .put("language", "javascript")
                    .put("views", new JsonObject()
                                    .put( DefaultView, new JsonObject()
                                            .put("map", script)
                                    )
                            // we might add another map-reduce script here for calculating some MOP...
                    );
            //https://docs.couchdb.org/en/stable/api/ddoc/common.html#put--db-_design-ddoc
            // fyi, its worth just trying stuff out in Fauxton and examining the network requests to get a clea idea what's going on

            promise.complete( design );
        });
        return promise.future();

    }
}
