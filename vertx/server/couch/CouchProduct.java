package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/** Constructs design documents for specific data products.
 * I think every product should have it's own design document.
 * Each product must have one or more views
 * an event view will most likely be the required one; it will correspond to events in the visualization, or rows in DRT.
 * Other views may be created for related analysis questions; such as MOPS.
 **/
public class CouchProduct {

    /** this should be the name provided to the design document in the mission database */
    public static final String DESIGN_NAME = "network";

    /** By convention each design should contain a default view named 'events'.
     * It should provide a chronological view of events in [time, source] order */
    static final String DEFAULT_VIEW = "events";

    /** This script contains a map function for the default event view */
    static final String EVENT_SCRIPT = "vertx\\server\\couch\\scripts\\network.js";

    public static Future<JsonObject> designDocument() {
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
                                    .put( DEFAULT_VIEW, new JsonObject()
                                            .put("map", script)
                                    )
                            // NOTE for example, you might add a reduce script here for calculating some MOP...
                    );
            //https://docs.couchdb.org/en/stable/api/ddoc/common.html#put--db-_design-ddoc
            // honestly, just look at the network requests of Fauxton when you manually create views. Much more informative.

            // next we have to

            promise.complete( design );
        });
        return promise.future();

    }

    public static Future<JsonObject> add(CouchClient client) {
        Promise<JsonObject> promise = Promise.promise();
        Vertx vertx = Vertx.currentContext().owner();

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
                                    .put( DEFAULT_VIEW, new JsonObject()
                                            .put("map", script)
                                    )
                            // NOTE for example, you might add a reduce script here for calculating some MOP...
                    );
            //https://docs.couchdb.org/en/stable/api/ddoc/common.html#put--db-_design-ddoc
            // honestly, just look at the network requests of Fauxton when you manually create views. Much more informative.

            // next we have to

            promise.complete( design );
        });
        return promise.future();
    }

}
