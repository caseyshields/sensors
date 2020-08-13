package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;

/** A collection of client methods for performing crud operations on Missions.
 * Missions are represented in CouchDB using databasses. Ideally every event that takes place
 * during a mission should be added to the mission database as a document.
 * @author casey */
public class Mission {

    /** Fetches a list of mission databases from the CouchDB REST API.
     * https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs
     * @return A json array of available databases as specified in CouchDB API. */
    public static Future<JsonArray> list(CouchClient client) {
        Promise<JsonArray> promise = Promise.promise();
        client.request(HttpMethod.GET, "/_all_dbs")
        .as(BodyCodec.jsonArray())
        .send( request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonArray> response = request.result();
            JsonArray databases = response.body();

            JsonArray missions = new JsonArray();
            databases.forEach( name -> {
                if (!name.toString().startsWith("_"))
                    missions.add( name.toString() );
            }); // todo probably should only return the ones that have valid umi's as names

            //client.printResponse(response);
            promise.complete(missions);
        });
        return promise.future();
    }

    /** Creates a new CouchDB database corresponding to the given mission umi using a HTTP Put request.
     * @param umi A Unique Mission Identifier */
    public static Future<Void> put(CouchClient client, String umi) {
        Promise<Void> promise = Promise.promise();
        client.request(HttpMethod.PUT, "/" + umi)
        .as(BodyCodec.jsonObject())
        .send( request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonObject> response = request.result();
            JsonObject body = response.body();
            if (body.containsKey("error"))
                promise.fail( body.toString() );
            else
                promise.complete();
        });
        return promise.future();
    }

    /** Obtains CouchDB summary information about the given mission database.
     * @param umi A Unique Mission Identifier
     * @return A Json Object containing database info as described in CouchDB API documentation*/
    public static Future<JsonObject> get(CouchClient client, String umi) {
        Promise<JsonObject> promise = Promise.promise();
        client.request(HttpMethod.GET, "/"+umi)
        .as(BodyCodec.jsonObject())
        .send( request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonObject> response = request.result();
            JsonObject body = response.body();
            promise.complete( body );
        });
        return promise.future();
    }

    /** Deletes the specified mission database.
     * todo I think couch might generate an error if the database is not empty?
     * @param umi A Unique Mission Identifier */
    public static Future<Void> delete(CouchClient client, String umi) {
        Promise<Void> promise = Promise.promise();
        client.request(HttpMethod.DELETE, "/"+umi)
        .as(BodyCodec.jsonObject())
        .send( request-> {
            if (!request.succeeded())
                promise.fail(request.cause());

            HttpResponse<JsonObject> response = request.result();
            JsonObject msg = response.body();
            if (msg.containsKey("error"))
                promise.fail( msg.toString() );

            promise.complete();
        });
        return promise.future();
    }
}
