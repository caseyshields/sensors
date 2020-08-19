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
public class Couch {

    Vertx vertx;
    WebClient client; // the client used to make HTTP requests to the CouchDB's REST API
    JsonObject token; // the current access token
    String host;
    int port;

    // TODO eventually things like current mission and roles should be stored in a user session...

    /** Obtain a session cookie using credentials in configuration.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public Couch(Vertx vertx, String host, int port) {
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

    /** Fetches a list of mission databases from the CouchDB REST API.
     * https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs
     * @return A json array of available databases as specified in CouchDB API. */
    public Future<JsonArray> list() {
        Promise<JsonArray> promise = Promise.promise();
        request(HttpMethod.GET, "/_all_dbs")
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

    /** https://docs.couchdb.org/en/stable/api/database/common.html#head--db
     * @return whether a database for the given mission exists */
    public Future<Database> get(String db) {
        Promise<Database> promise = Promise.promise();

        request(HttpMethod.HEAD, "/"+db)
        .send( request -> {
            if (!request.succeeded())
                promise.fail( request.cause() );

            HttpResponse response = request.result();
            if (response.statusCode()==200) {
                Database database = new Database(this, db);
                promise.complete(database);
            }
//            else if (response.statusCode()==404)
//                promise.complete( null ); // should I fail? prob should be distinct from a network error...
            else promise.fail("Invalid Status Code");
        });
        return promise.future();
    }

    /** Creates a new CouchDB database corresponding to the given mission umi using a HTTP Put request.
     * @param db A Unique Mission Identifier
     * https://docs.couchdb.org/en/stable/api/database/common.html#put--db */
    public Future<Database> put( String db ) {
        Promise<Database> promise = Promise.promise();

        request(HttpMethod.PUT, "/" + db)
                .as(BodyCodec.jsonObject())
                .send( request -> {
                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();
                    if (body.containsKey("error"))
                        promise.fail( body.toString() );

                    Database database = new Database(this, db);
                    promise.complete( database );
                });

        return promise.future();
    }

    /** Obtains CouchDB summary information about the given mission database.
     * https://docs.couchdb.org/en/stable/api/database/common.html#get--db
     * @param db the name of the couchdb database
     * @return A Json Object containing database info as described in CouchDB API documentation*/
    public Future<JsonObject> info( String db ) {
        Promise<JsonObject> promise = Promise.promise();

        request(HttpMethod.GET, "/"+db)
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

    /** Deletes the specified mission database.
     * https://docs.couchdb.org/en/stable/api/database/common.html#delete--db
     * @param db the name of the database */
    public Future<Void> delete(String db) {
        Promise<Void> promise = Promise.promise();

        request(HttpMethod.DELETE, "/"+db)
                .as(BodyCodec.jsonObject())
                .send( request-> {
                    if (!request.succeeded())
                        promise.fail(request.cause());

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject msg = response.body();
                    if (msg.containsKey("error"))
                        promise.fail( msg.toString() );
                    else
                        promise.complete();
                });
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