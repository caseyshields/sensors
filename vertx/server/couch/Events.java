package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

// TODO flesh out with post, and bulk operations...
public class Events {

    /** Get the 'i'th document in key order from the database. */
    static public Future<JsonObject> get(CouchClient client, String umi, String product ) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + umi
                + "/_design/" + product
                + "/_view/" + Product.DefaultView;
        //        "?include_docs=true&limit=1&skip=" + index; // part of a paging scheme- I don't think they do it this way anymore
        String cookie = "AuthSession=" + client.token.getString("AuthSession");

        // fetch the results
        HttpRequest<JsonObject> getDoc = client.client.get(client.port, client.host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .addQueryParam("limit", "10")
//                .addQueryParam("startkey", "")
//                .addQueryParam("endkey", "")
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


}
