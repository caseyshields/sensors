package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;

/** Provides CRUD operations for Missions.
 * Missions are represented in CouchDB using databases.
 * Every event that takes place during a mission should be added to the mission database as a document.
 * @author casey */
public class Database {

    Couch client;
    String db;

    public Database(Couch client, String db) {
        this.client = client;
        this.db = db;
        // todo make private and add builder to couch client...
    }

    public Couch getClient() { return client; }
    public String getName() { return db; }

    /** Get a specific event by it's document id.
     * https://docs.couchdb.org/en/stable/api/document/common.html#get--db-docid
     * @return The document with the requested id in a JsonObject. */
    public Future<JsonObject> get(String id) {
        Promise<JsonObject> promise = Promise.promise();
        client.request(HttpMethod.GET, "/"+ db +"/"+id)
                .as(BodyCodec.jsonObject())
                .send( request -> {

                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();
                    if (body.containsKey("error"))
                        promise.fail( body.toString() );
                    else
                        promise.complete( body );
                });
        return promise.future();
    }

    /** Add an event to the Mission database. It will be indexed in chronological, source order.
     * */
    public Future<JsonObject> put(String id, JsonObject event) {
        Promise<JsonObject> promise = Promise.promise();

        client.request(HttpMethod.PUT, "/"+ db +"/"+id)
                .as(BodyCodec.jsonObject())
                .sendJsonObject( event, request -> {

                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();
                    if (body.containsKey("error"))
                        promise.fail( body.toString() );
                    else
                        promise.complete(body);
                });
        return promise.future();
    }

    /** Get all events from the mission between the two keys. */
    public Future<JsonObject> get(String start, String end) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        client.request(HttpMethod.GET, '/' + db + "/_all_docs")
                .addQueryParam( "include_docs", "true")
                .addQueryParam("startkey", '"'+start+'"')
                .addQueryParam("endkey", '"'+end+'"' )
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject json = response.body();

                    if (json.containsKey("error"))
                        promise.fail( json.toString() );

                    promise.complete( json );
                });

        return promise.future();
    }

    public Future<JsonObject> get(String start, Integer limit) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        client.request(HttpMethod.GET, '/' + db + "/_all_docs")
                .addQueryParam( "include_docs", "true")
                .addQueryParam("startkey", '"'+start+'"')
                .addQueryParam("limit", limit.toString() )
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject json = response.body();

                    if (json.containsKey("error"))
                        promise.fail( json.toString() );

                    promise.complete( json );
                });

        return promise.future();
    } // TODO should I just return an array with the documents, stripping out the redundant view index info?

}
