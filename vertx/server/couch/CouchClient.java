package server.couch;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

import java.util.Arrays;

/** So a Vertx client is already written for MongoDB...
 * might want to just switch to that rather than trying to write our own interface.
 * then again, will have to figure something out for interoperability with dolphin,
 * the new simulator, and we have to figure out queries and view over again...*/
public class CouchClient {

    Vertx vertx;
    WebClient client; // the client used to make HTTP requests to the CouchDB's REST API
    JsonObject token; // the current access token
    String host;
    int port;

    // TODO eventually things like current mission and roles should be stored in a user session...

    /** Obtain a session cookie using credentials in configuration.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public CouchClient(Vertx vertx, String host, int port) {
        this.vertx = vertx; //Vertx.currentContext().owner();
        this.client = WebClient.create(vertx);
        this.host = host;
        this.port = port;
    }

    public Future<JsonObject> getSession(String name, String password) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject credentials = new JsonObject()
                .put("name", name)
                .put("password", password);

        HttpRequest<JsonObject> session = client.post(port, host, "/_session")
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .putHeader("Content-Length", Integer.toString(credentials.toString().length()))
                .expect(ResponsePredicate.status(200, 299))
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        session.sendJsonObject(credentials, request -> {

            if (!request.succeeded())
                promise.fail(request.cause());
            HttpResponse<JsonObject> response = request.result();
//            printResponse( response );

            // make sure we have admin role
            JsonObject body = response.body();
            if (body.containsKey("error"))
                promise.fail(body.getString("error"));
            if (!body.getJsonArray("roles").contains("_admin"))
                promise.fail("not an administrator");
            // TODO hmm, will I eventually need any of the role information?

            // parse and return the session cookie as a JsonObject
            String cookie = response.getHeader("Set-Cookie");
            JsonObject token = parseCookie(cookie);
            this.token = token;
            promise.complete( token );
        });
        return promise.future();
    }

    /** https://docs.couchdb.org/en/stable/api/server/authn.html#delete--_session */
    public Future<Void> deleteSession() {
        return Future.future( promise -> {
            request(HttpMethod.DELETE, "/_session")
            .as(BodyCodec.jsonObject())
            .send( request -> {
                if (!request.succeeded())
                    promise.fail(request.cause());
                HttpResponse<JsonObject> response = request.result();

                JsonObject body = response.body();
                if (!body.getBoolean("ok"))
                    promise.fail(body.getString("error"));

                promise.complete();
            });
        });
    }

    public Future<Void> close() {
        Promise<Void> promise = Promise.promise();
        deleteSession()
                .onSuccess( r->promise.complete() )
                .onFailure( r->promise.fail(r.getCause()) );
        return promise.future();
    }

    HttpRequest<?> request(HttpMethod method, String uri) {
        String cookie = "AuthSession=" + token.getString("AuthSession");
        return client.request(method, port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON);
    }

    /** The CouchDB authorization cookie is a semicolon delimited set of name value pairs. */
    private JsonObject parseCookie (String cookie) {
        String[] entries = cookie.split(";");
        JsonObject token = new JsonObject();
        Arrays.stream(entries).forEach(entry->{
            String[] pair = entry.split("=");
            if (pair.length==2)
                token.put(pair[0].trim(), pair[1].trim());
            else if (pair.length==1)
                token.put(pair[0].trim(), "");
        });
        return token;
    }

    void printRequest(HttpRequest<?> request) {
        System.out.println("HEADERS:");
        request.headers().forEach( header->
            System.out.println( header.getKey()+'='+header.getValue()) );
    }

    void printResponse(HttpResponse<?> response) {
        System.out.println("STATUS:"+response.statusCode()+"="+response.statusMessage());

        System.out.println("HEADERS:");
        response.headers().forEach( header->
                System.out.println( header.getKey()+'='+header.getValue()) );

        System.out.println("COOKIES:");
        response.cookies().forEach( System.out::println );

        System.out.println("BODY:");
        System.out.println( response.body() );

        System.out.println("TRAILERS:");
        response.trailers().forEach( System.out::println );
    }

// once I see some common patterns in the couch API I might want to make some custom response Predicates...
//    Function<HttpResponse<Void>, ResponsePredicateResult> methodsPredicate = resp -> {
//        String methods = resp.getHeader("Access-Control-Allow-Methods");
//        if (methods != null) {
//            if (methods.contains("POST")) {
//                return ResponsePredicateResult.success();
//            }
//        }
//        return ResponsePredicateResult.failure("Does not work");
//    };
}

//    public Future<JsonObject> getSession(String name, String password) {
//        Promise<JsonObject> promise = Promise.promise();
//
//        JsonObject credentials = new JsonObject()
//                .put("name", name)
//                .put("password", password);
//
//        request(HttpMethod.POST, "/_session", credentials, request -> {
//
//            if (!request.succeeded())
//                promise.fail(request.cause());
//            HttpResponse<JsonObject> response = request.result();
////            printResponse( response );
//
//            // make sure we have admin role
//            JsonObject body = response.body();
//            if (body.containsKey("error"))
//                promise.fail(body.getString("error"));
//            if (!body.getJsonArray("roles").contains("_admin"))
//                promise.fail("not an administrator");
//            // TODO hmm, will I eventually need any of the role information?
//
//            // parse and return the session cookie as a JsonObject
//            String cookie = response.getHeader("Set-Cookie");
//            JsonObject token = parseCookie(cookie);
//            this.token = token;
//            promise.complete( token );
//        });
//        return promise.future();
//    }