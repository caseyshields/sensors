package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import server.couch.designs.Design;

/** Provides CRUD operations for mission data products.
 * Our products correspond to CouchDb design documents with just one view.
 * @author casey */
public class Product {

    static String DefaultView = "events";

    public static Future<JsonArray> list(CouchClient client, String umi) {
        Promise<JsonArray> promise = Promise.promise();
        client.request( HttpMethod.GET, "/"+umi+"/_design_docs")
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
    public Future<JsonObject> get(CouchClient client, String umi, String product) {
        Promise<JsonObject> promise = Promise.promise();

        String uri = "/" + umi + "/_design/" + product;
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
    public Future<Void> put(CouchClient client, String umi, Design design) {
        Promise<Void> promise = Promise.promise();

        design.getDesignDocument()
        .onSuccess( document -> {

            String uri = "/" + umi + "/_design/" + design.getName();
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

}
