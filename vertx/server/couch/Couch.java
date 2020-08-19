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

/** Provides a client interface for a couchDB instance
 * @author Casey  */
public class Couch {

    Vertx vertx;
    WebClient client;
    JsonObject token;
    String host;
    int port;
    // todo eventually things like current mission and roles should be stored in a user session...

    /** Initializes a client but does not connect
     * @param vertx a Vert.x context
     * @param host the ip or url of the CouchDB instance
     * @param port the CouchDB REST API port. Usually http is on 5984, and https is on 6984 */
    public Couch(Vertx vertx, String host, int port) {
        this.vertx = vertx; //Vertx.currentContext().owner();
        this.client = WebClient.create(vertx);
        this.host = host;
        this.port = port;
    }

    /** Obtain a session cookie and caches them in the client.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
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

    /** Asynchronously produces the database if it exists in CouchDB, otherwise fails.
     * https://docs.couchdb.org/en/stable/api/database/common.html#head--db
     * @return a Future producing the Database */
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
//            else promise.fail("Invalid Status Code");
            promise.fail("Database \""+db+"\" does not exist");
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
}
