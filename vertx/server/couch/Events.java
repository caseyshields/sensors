package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/** Interface for uploading events, and retrieving them through default and product views
 * @author casey */
public class Events {

    /** Get a specific event by it's document id.
     * https://docs.couchdb.org/en/stable/api/document/common.html#get--db-docid
     * @return The document with the requested id in a JsonObject. */
    static public Future<JsonObject> get(CouchClient client, String umi, String id) {
        Promise<JsonObject> promise = Promise.promise();
        client.request(HttpMethod.GET, "/"+umi+"/"+id)
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
        /** TODO what should we use for the document id by convention?
         * I'm thinking [source, address]. This is because views already provide a chronological view, and I can recall more
         * than a few times we tried to associate raw file lines with records. This will make it trivial...*/
    }

    /** Get the 'i'th document in key order from the database. */
    static public Future<JsonObject> get(CouchClient client, String umi, String product,
                                         String start, String end ) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + umi
                + "/_design/" + product
                + "/_view/" + Product.DefaultView;
        String cookie = "AuthSession=" + client.token.getString("AuthSession");

        // fetch the results
        HttpRequest<JsonObject> getDoc = client.client.get(client.port, client.host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
//                .addQueryParam("limit", "10")
                // include_docs=true
                // limit=10
                // skip=10
                .addQueryParam("startkey", start)
                .addQueryParam("endkey", end)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        getDoc.send(request -> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
//                printResponse(response);
                promise.complete( response.body() );
            } else
                promise.fail( request.cause() );
        });

        return promise.future();
    }
    // http://localhost:5984/sensors/_design/product/_view/events?startkey=[%222020-07-17T18:30:44.752Z%22,%22s3%22]&endkey=[%222020-07-17T18:30:44.952Z%22,%22d2%22]

    /** Add an event to the Mission database. It will be indexed in chronological, source order. */
    static public Future<JsonObject> put(CouchClient client, String umi,
                                         String time, String source,
                                         JsonObject event) {
        Promise<JsonObject> promise = Promise.promise();
        client.request(HttpMethod.PUT, "/"+umi+"/"+time+"-"+source)
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
    static public Future<JsonObject> get(CouchClient client, String umi,
                                         String start, String end ) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + umi + "/_all_docs";
        //        "?include_docs=true&limit=1&skip=" + index; // part of a paging scheme- I don't think they do it this way anymore
        String cookie = "AuthSession=" + client.token.getString("AuthSession");

        // fetch the results
        HttpRequest<JsonObject> getDoc = client.client.get(client.port, client.host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
//                .addQueryParam("limit", "10")
                .addQueryParam( "include_docs", "true")
                .addQueryParam("startkey", '"'+start+'"')
                .addQueryParam("endkey", '"'+end+'"')
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        getDoc.send(request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonObject> response = request.result();
            //printResponse(response);
            JsonObject json = response.body();

            if (json.containsKey("error"))
                promise.fail( json.toString() );

            promise.complete( json );
        });

        return promise.future();
    }

    static public Future<JsonObject> get(
            CouchClient client, String umi, String start, Integer limit
    ) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        client.request(HttpMethod.GET, '/' + umi + "/_all_docs")
        .addQueryParam( "include_docs", "true")
        .addQueryParam("startkey", '"'+start+'"')
        .addQueryParam("limit", limit.toString() )
        .as(BodyCodec.jsonObject())
        .send(request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse<JsonObject> response = request.result();
            //printResponse(response);
            JsonObject json = response.body();

            if (json.containsKey("error"))
                promise.fail( json.toString() );

            promise.complete( json );
        });

        return promise.future();
    } // TODO should I just return an array with the documents, stripping out the redundant view index info?


    // the recommended best pratice is to avoid UUID keys and supply your own meaningful key scheme, so instead we'll flesh out the 'put' inteface...
//    /** Adds the event to the given mission. To supply an index provide an '_id' field in the event object.
//     * A document can be updated if you provide a current revision number in the '_rev' field of the event,
//     * or in the 'If-Match' header of the request...
//     * https://docs.couchdb.org/en/stable/api/database/common.html#post--db
//     * @return A JsonObject with the CouchDB id and revision number */
//    static public Future<JsonObject> post(CouchClient client, String umi, JsonObject event) {
//        Promise<JsonObject> promise = Promise.promise();
//
//        // post the document in the given mission database
//        client.request(HttpMethod.POST, "/"+umi)
//        // another option is to PUT at '/umi/_id' if you already have the id
//
//        // increases throughput but provides fewer guarantees.
//        //.addQueryParam("batch", "ok")
//        // Definitely worth investigating if we reach a bottleneck
//
//        .as( BodyCodec.jsonObject() )
//        .sendJsonObject( event, request -> {
//
//            if (!request.succeeded())
//                promise.fail( request.cause() );
//
//            HttpResponse<JsonObject> response = request.result();
//            JsonObject body = response.body();
//            if (body.containsKey("error"))
//                promise.fail( body.toString() );
//            else
//                promise.complete(body);
//        });
//
//        return promise.future();
//    }

}
