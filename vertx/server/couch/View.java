package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;
import server.couch.designs.Design;

/** Provides CRUD operations for mission data products.
 * Our products correspond to CouchDb design documents with just one view.
 * @author casey */
public class View {

    static String DefaultView = "events";
    Couch client;
    String db;

    public Future<JsonArray> list() {
        Promise<JsonArray> promise = Promise.promise();
        client.request( HttpMethod.GET, "/"+ db +"/_design_docs")
        .as(BodyCodec.jsonObject())
        .send( request -> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
                JsonObject body = response.body();
                JsonArray products = new JsonArray();

                // get the list of design documents
                JsonArray rows = body.getJsonArray("rows");
                rows.forEach( row -> {

                    // trim the conventional design document prefix
                    String id = ((JsonObject)row).getString("id");
                    String view = id.substring( 1 + id.lastIndexOf("/") );

                    // the views correspond to data products
                    products.add( view );
                });
                promise.complete(products);
            } else
                promise.fail( request.cause() );
        } );
        return promise.future();
    }

    /** Retrieves the design document for the mission data product. */
    public Future<JsonObject> get(String product) {
        Promise<JsonObject> promise = Promise.promise();

        String uri = "/" + db + "/_design/" + product;
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

    /** Adds the design document to the mission database. */
    public Future<Void> put(Design design) {
        Promise<Void> promise = Promise.promise();

        design.getDesignDocument()
        .onSuccess( document -> {

            String uri = "/" + db + "/_design/" + design.getName();
            client.request(HttpMethod.PUT, uri)
            .as(BodyCodec.jsonObject())
            .sendJsonObject(document, request -> {

                if (!request.succeeded())
                    promise.fail(request.cause());

                HttpResponse<JsonObject> response = request.result();
                JsonObject message = response.body();

                if (message.containsKey("error"))
                    promise.fail(message.toString());
                else
                    promise.complete();
            });
        }).onFailure( promise::fail );

        return promise.future();
    }

    /** Get the specified number of events starting from the given key.
     * */
    public Future<JsonObject> get(String product, String start, Integer size) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + db
                + "/_design/" + product
                + "/_view/" + View.DefaultView;
        client.request(HttpMethod.GET, uri)
                .addQueryParam("startkey", '"'+start+'"')
                .addQueryParam("limit", size.toString() )
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
