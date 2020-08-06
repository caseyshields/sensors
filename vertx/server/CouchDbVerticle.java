package server;

import io.vertx.core.*;
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
public class CouchDbVerticle extends AbstractVerticle {

    private static final String VIEW_DIR = "vertx\\server\\views\\";

    WebClient client; // the client used to make HTTP requests to the CouchDB's REST API
    JsonObject token; // the current access token // TODO this will go into user sessions

    // TODO eventually things like current mission and roles should be stored in a user session...

    /** Obtain a session cookie using credentials in configuration.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public void start(Promise<Void> promise) {
        this.client = WebClient.create(vertx);
        JsonObject credentials = config().getJsonObject("credentials");

        Future<JsonObject> session = getSession(
                credentials.getString("name"),
                credentials.getString("password")
        ).onSuccess(
            token -> this.token = token
        ).onFailure( promise::fail );
    }

    public void stop(Promise<Void> promise) {
        deleteSession()
                .onSuccess( r->promise.complete() )
                .onFailure( r->promise.fail(r.getCause()) );
    }

    private Future<JsonObject> getSession(String name, String password) {
        Promise<JsonObject> promise = Promise.promise();

        JsonObject credentials = new JsonObject()
                .put("name", name)
                .put("password", password);

        HttpRequest<JsonObject> session = client.post(5984, "localhost", "/_session")
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
            if (!body.getBoolean("ok"))
                promise.fail(body.getString("error"));
            if (!body.getJsonArray("roles").contains("_admin"))
                promise.fail("not an administrator");
            // TODO hmm, will I eventually need any of the role information?

            // parse and return the session cookie as a JsonObject
            String cookie = response.getHeader("Set-Cookie");
            JsonObject token = parseCookie(cookie);
            promise.complete( token );
        });
        return promise.future();
    }

    /** Creates a database in CouchDB corresponding to a mission, and adds design documents for the needed views*/
    private Future<Void> createMission(String umi) {
        Promise<Void> promise = Promise.promise();

        String host = config().getString("host");
        int port = config().getInteger("port");
        String uri = "/" + umi;
        String cookie = "AuthSession=" + token.getString("AuthSession");
        HttpRequest<JsonObject> put = client.put(port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        put.send( request -> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
                JsonObject message = response.body();
                if (message.getBoolean("ok"))
                    promise.complete();
                else
                    promise.fail( message.toString() );
            } else
                promise.fail( request.cause() );

        });

        return promise.future();
    }

    /** Creates a database in CouchDB corresponding to a mission, and adds design documents for the needed views*/
    private Future<JsonObject> getMission(String umi) {
        Promise<JsonObject> promise = Promise.promise();

        String host = config().getString("host");
        int port = config().getInteger("port");
        String uri = "/" + umi;
        String cookie = "AuthSession=" + token.getString("AuthSession");
        HttpRequest<JsonObject> mission = client.get(port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        mission.send( request -> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
                promise.complete( response.body() );
            } else
                promise.fail( request.cause() );
        });

        return promise.future();
    }

    /** create the specified design document from scripts on the classpath
     * TODO we might want multiple views, or reduce functions, etc. Need to think about conventions for this... */
    private Future<JsonObject> loadDesign(String name ) {
        Promise promise = Promise.promise();

        String path = VIEW_DIR + name + ".js";
        vertx.fileSystem().readFile( path, read -> {

            // check for file problems
            if (!read.succeeded())
                promise.fail(read.cause());
            String script = read.result().toString();
            if (script==null || script.length()==0)
                promise.fail("Error: map script missing.");

            // create a design document object for the CouchDB API
            JsonObject design = new JsonObject()
                .put("language", "javascript")
                .put("views", new JsonObject()
                    .put( name, new JsonObject()
                        .put("map", script)
                    )
                );
            //https://docs.couchdb.org/en/stable/api/ddoc/common.html#put--db-_design-ddoc
            // honestly, just look at the network requests of Fauxton when you manually create views. Much more informative.

            promise.complete( design );
        });
        return promise.future();
    }

    /** Adds the design document associated with the given product to the mission indicated by the umi. */
    private Future<Void> addProduct(String umi, String product) {
        Promise promise = Promise.promise();

        // get the view's map function script for the specified product
        loadDesign(product).onSuccess(design -> {

            // create a request
            String host = config().getString("host");
            int port = config().getInteger("port");
            String uri = "/" + umi + "/_design/" + product;
            String cookie = "AuthSession=" + token.getString("AuthSession");
            int length = design.toString().length(); // redundant computation, does the request work without this header?
            HttpRequest<JsonObject> put = client.put(port, host, uri)
                    .putHeader("Accept", "application/json")
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Content-Length", Integer.toString(length))
                    .putHeader("Cookie", cookie)
                    .expect(ResponsePredicate.JSON)
                    .as(BodyCodec.jsonObject());

            // send request and handle the response
            put.sendJson( design, request -> {
                if (request.succeeded()) {
                    HttpResponse<JsonObject> response = request.result();
//                    printResponse(response);
                    JsonObject message = response.body();
                    if (message.getBoolean("ok"))
                        promise.complete();
                    else promise.fail( message.toString() );
                } else
                    promise.fail( request.cause() );
            });

        }).onFailure( promise::fail );

        return promise.future();
    }

    /** https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs
     * @return An array of available databases as specified in CouchDB API. */
    private Future<JsonArray> getDatabases() {
        Promise<JsonArray> promise = Promise.promise();

        // craft an Http request for the couch api endpoint
        String host = config().getString("host");
        int port = config().getInteger("port");
        String cookie = "AuthSession=" + token.getString("AuthSession");
        HttpRequest<JsonArray> dbs = client.get(port, host, "/_all_dbs")
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonArray());

        // send it asynchronously
        dbs.send( request -> {
            if (request.succeeded()) {
                HttpResponse<JsonArray> response = request.result();
                // TODO instead only return missions...
//                printResponse(response);
                promise.complete(response.body());
            } else
                promise.fail( request.cause() );
        });
        return promise.future();
    }

    /** Get the 'i'th document in key order from the database. */
    private Future<JsonObject> getDocument( String umi, String product, int index ) {
        Promise promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String host = config().getString("host");
        int port = config().getInteger("port");
        String uri = '/' + config().getString("db");// +
        String cookie = "AuthSession=" + token.getString("AuthSession");
//                "/_design/" + config().getString("design") +
//                "/_view/" + config().getString("view") +
//                "?include_docs=true&limit=1&skip=" + index;

        // fetch the results
        HttpRequest<JsonObject> getDoc = client.get(port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        getDoc.send(request -> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
                printResponse(response);
                promise.complete(response.body());
            } else
                promise.fail( request.cause() );
        });

        return promise.future();
    }

    /** https://docs.couchdb.org/en/stable/api/server/authn.html#delete--_session */
    private Future<Void> deleteSession() {
        return Future.future( promise -> {

            HttpRequest<JsonObject> delete = client.delete(5984, "localhost", "/_session")
                    .putHeader("Accept", "application/json")
                    .putHeader("AuthSession", token.getString("AuthSession"))
                    .expect(ResponsePredicate.status(200, 299))
                    .expect(ResponsePredicate.JSON)
                    .as(BodyCodec.jsonObject());

            delete.send( request -> {
                if (!request.succeeded())
                    promise.fail(request.cause());
                HttpResponse<JsonObject> response = request.result();

                JsonObject body = response.body();
                if (!body.getBoolean("ok"))
                    promise.fail(body.getString("error"));

                System.out.println(body);
                promise.complete();
            });
        });
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

    private void printRequest(HttpRequest<?> request) {
        System.out.println("HEADERS:");
        request.headers().forEach( header->
            System.out.println( header.getKey()+'='+header.getValue()) );
    }

    private void printResponse(HttpResponse<?> response) {
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
