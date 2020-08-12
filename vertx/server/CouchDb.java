package server;

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
public class CouchDb {

    Vertx vertx;
    WebClient client; // the client used to make HTTP requests to the CouchDB's REST API
    JsonObject token; // the current access token
    String host;
    int port;

    // TODO eventually things like current mission and roles should be stored in a user session...

    /** Obtain a session cookie using credentials in configuration.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public CouchDb(Vertx vertx, String host, int port) {
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

    /** https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs
     * @return An array of available databases as specified in CouchDB API. */
    public Future<JsonArray> getMissions() {
        Promise<JsonArray> promise = Promise.promise();

        // craft an Http request for the couch api endpoint
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
                JsonArray databases = response.body();

                JsonArray missions = new JsonArray();
                databases.forEach( name -> {
                    if (!name.toString().startsWith("_"))
                        missions.add( name.toString() );
                });

//                printResponse(response);
                promise.complete(missions);
            } else
                promise.fail( request.cause() );
        });
        return promise.future();
    }

    /** Creates a database in CouchDB corresponding to a mission, and adds design documents for the needed views*/
    public Future<Void> createMission(String umi) {
        Promise<Void> promise = Promise.promise();

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
                if (message.containsKey("error"))
                    promise.fail( message.toString() );
                else
                    promise.complete();
            } else
                promise.fail( request.cause() );

        });

        return promise.future();
    }

    /** Creates a database in CouchDB corresponding to a mission, and adds design documents for the needed views*/
    public Future<JsonObject> getMission( String umi ) {
        Promise<JsonObject> promise = Promise.promise();

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

    /** Creates a database in CouchDB corresponding to a mission, and adds design documents for the needed views*/
    public Future<JsonObject> deleteMission( String umi ) {
        Promise<JsonObject> promise = Promise.promise();
        String uri = "/" + umi;

        request(HttpMethod.DELETE, uri, request-> {
            if (request.succeeded()) {
                HttpResponse<JsonObject> response = request.result();
                promise.complete( response.body() );
            } else
                promise.fail( request.cause() );
        });
        return promise.future();
    }

    private void request(HttpMethod method, String uri,
                         Handler<AsyncResult<HttpResponse<JsonObject>>> handler) {
        String cookie = "AuthSession=" + token.getString("AuthSession");
        client.request(method, port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject())
                .send( handler );
    }

    public Future<JsonArray> getProducts(String umi) {
        Promise<JsonArray> promise = Promise.promise();
        String uri = "/"+umi+"/_design_docs";
        String cookie = "AuthSession=" + token.getString("AuthSession");
        HttpRequest<JsonObject> get = client.get(port, host, uri)
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());
        get.send( request -> {
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

    /** Adds the design document associated with the given product to the mission indicated by the umi. */
    public Future<Void> addProduct(String umi, String product) {
        Promise<Void> promise = Promise.promise();

        // get the view's map function script for the specified product
        Product.loadDesign(product).onSuccess(design -> {

            // create a request
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
                    if (message.containsKey("error"))
                        promise.fail( message.toString() );
                    else
                        promise.complete();
                } else
                    promise.fail( request.cause() );
            });

        }).onFailure( promise::fail );

        return promise.future();
    }

    /** Get the 'i'th document in key order from the database. */
    public Future<JsonObject> getEvents( String umi, String product ) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        String uri = '/' + umi
                + "/_design/" + product
                + "/_view/" + Product.DEFAULT_VIEW;
        //        "?include_docs=true&limit=1&skip=" + index; // part of a paging scheme- I don't think they do it this way anymore
        String cookie = "AuthSession=" + token.getString("AuthSession");

        // fetch the results
        HttpRequest<JsonObject> getDoc = client.get(port, host, uri)
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

    /** https://docs.couchdb.org/en/stable/api/server/authn.html#delete--_session */
    public Future<Void> deleteSession() {
        return Future.future( promise -> {

            String cookie = "AuthSession=" + token.getString("AuthSession");

            HttpRequest<JsonObject> delete = client.delete(port, host, "/_session")
                    .putHeader("Accept", "application/json")
                    .putHeader("Cookie", cookie )//token.getString("AuthSession"))
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
