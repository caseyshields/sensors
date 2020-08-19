package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;
import server.couch.designs.Design;

/** Provides a client for a CouchDB Design document.
 * @author casey */
public class View {

    Couch client;
    String db;
    String design;

    /** todo technically a design document can have many views of different types. We'll cross that bridge when the need arises... */
    static final String DefaultView = "events";

    /** Creates a view client without actually checking Couch if any such design document actually exists
     * @param client the CouchDB client which will perform all the http requests
     * @param db the name of the database being viewed
     * @param design the name of the design document */
    public View(Couch client, String db, String design) {
        this.client = client;
        this.db = db;
        this.design = design;
    }

    /** Retrieves the design document. */
    public Future<JsonObject> getDesignDocument() {
        Promise<JsonObject> promise = Promise.promise();

        String uri = "/" + db + "/_design/" + design;
        client.request(HttpMethod.GET, uri)
        .as( BodyCodec.jsonObject() )
        .send( request -> {

            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonObject> response = request.result();
            JsonObject body = response.body();

            promise.complete( body );
        });
        return promise.future();
    } // TODO add a variant that uses a HTTP Head command for 'has' predicates...

    /** Get documents from the default view of this design
     * @param startkey the minimum key in lexical order, inclusive
     * @param endkey the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> getDocs(String startkey, String endkey) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + db
                + "/_design/" + design
                + "/_view/" + View.DefaultView;
        client.request(HttpMethod.GET, uri)
                .addQueryParam("startkey", '"'+startkey+'"')
                .addQueryParam("endkey", '"'+endkey+'"')
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

    /** Get documents from th e default view of this design
     * @param startkey the minimum key in lexical order, inclusive
     * @param limit the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> getDocs(String startkey, Integer limit) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + db
                + "/_design/" + design
                + "/_view/" + View.DefaultView;
        client.request(HttpMethod.GET, uri)
                .addQueryParam("startkey", '"'+startkey+'"')
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

}
