package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;

/** Provides a client for a CouchDB Views, which allow you to fetch contiguous key intervals of documents from the database.
 * @author casey */
public class View {

    Couch client;
    String db;
    String view;

    /** All CouchDB databases provide a default view of all documents keyed by their IDs. */
    public static final String DefaultView = "/_all_docs";

    /** Creates a client for the default view of the database, '_all_docs'
     * @param client the CouchDB client which will perform all the http requests
     * @param db the name of the database being viewed */
    public View(Couch client, String db) {
        this.client = client;
        this.db = db;
        this.view = DefaultView;
    }

    /** Creates a view client for the specific design view
     * @param client the CouchDB client which will perform all the http requests
     * @param db the name of the database being viewed
     * @param design the name of the design document
     * @param view The name of the view */
    public View(Couch client, String db, String design, String view) {
        this.client = client;
        this.db = db;
        this.view = "/_design/" + design + "/_view/" + view;
    }

    /** Get documents from the default view of this design
     * @param startkey the minimum key in lexical order, inclusive
     * @param endkey the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> getDocs(String startkey, String endkey) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + db + view;
        client.request(HttpMethod.GET, uri)
                .addQueryParam("startkey", startkey)
                .addQueryParam("endkey", endkey)
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (request.succeeded()) {
                        HttpResponse<JsonObject> response = request.result();
//                printResponse(response);
                        promise.complete( response.body() );
                    } else
                        promise.fail( request.cause() );
                });

        return promise.future();
    } // TODO maybe figure out a more fluent way to set query parameters...

    /** Get documents from th e default view of this design
     * @param startkey the minimum key in lexical order, inclusive
     * @param limit the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> getDocs(String startkey, Integer limit) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + db + view;
        client.request(HttpMethod.GET, uri)
                .addQueryParam("startkey", startkey)
                .addQueryParam("limit", limit.toString() )
                //.addQueryParam("endkey", end)
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (request.succeeded()) {
                        HttpResponse<JsonObject> response = request.result();
//                printResponse(response);
                        promise.complete( response.body() );
                    } else
                        promise.fail( request.cause() );
                });

        return promise.future();
    }

    // todo add methods for document update and delete that only works in the default view?
}
