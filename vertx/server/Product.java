package server;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/** Constructs design documents for specific data products.
 * I think every product should have it's own design document.
 * Each product must have one or more views
 * an event view will most likely be the required one; it will correspond to events in teh visalization, or rows in DRT.
 * Other views may be created for related analysis questions; such as MOPS.
 **/
public class Product {

    private static final String VIEW_DIR = "vertx\\server\\views\\";

    /** create the specified design document from scripts on the classpath
     * TODO we might want multiple views, or reduce functions, etc. Need to think about conventions for this... */
    public static Future<JsonObject> loadDesign(String name ) {
        Vertx vertx = Vertx.currentContext().owner();
        Promise promise = Promise.promise();

        String path = VIEW_DIR + name + ".js";
        vertx.fileSystem().readFile( path, read -> {

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
                            .put( name, new JsonObject()
                                    .put("map", script)
                            )
                    );
            //https://docs.couchdb.org/en/stable/api/ddoc/common.html#put--db-_design-ddoc
            // honestly, just look at the network requests of Fauxton when you manually create views. Much more informative.

            promise.complete( design );
        });
        return promise.future();
    }
}
