package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;
import server.couch.designs.Design;

/** Represents a Design document and provides access to its views.
 * @author casey */
public class View {

    static String DefaultView = "events";
    Couch client;
    String db;
    String design;

    public View(Couch client, String db, String design) {
        this.client = client;
        this.db = db;
        this.design = design;
    }

    /** Retrieves the design document. */
    public Future<JsonObject> get() {
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

    /** Get documents from this view
     * @param startkey the minimum key in lexical order, inclusive
     * @param endkey the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> get(String startkey, String endkey) {
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

    /** Get documents from this view
     * @param startkey the minimum key in lexical order, inclusive
     * @param limit the maximum key in lexical order, inclusive
     * */
    public Future<JsonObject> get(String startkey, Integer limit) {
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
    } // TODO we might need to eventually support more than one view per design...

}
