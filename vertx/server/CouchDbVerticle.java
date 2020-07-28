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

    WebClient client; // the client used to make HTTP requests to the CouchDB's REST API
    JsonObject token; // the current access token

    public static void main(String[] args) {
        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("port", 5984)
                .put("db", "sensors")
                .put("design", "stream")
                .put("view", "chronological")
                .put("credentials", new JsonObject()
                        .put("name", "admin")
                        .put("password", "Preceptor"));

        DeploymentOptions options = new DeploymentOptions()
                //.setWorker( true )
                .setConfig( config );
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new CouchDbVerticle(), options);
    }

    /** Obtain a session cookie, then check to make sure there is data in the configured database.
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public void start(Promise<Void> promise) {
        this.client = WebClient.create(vertx);

        // get a session token for the configured user
        JsonObject credentials = config().getJsonObject("credentials");
        Future<JsonObject> session = getSession(
                credentials.getString("name"),
                credentials.getString("password"));

        // cache the token then get all available databases
        Future<JsonArray> dbs = session.compose( token -> {
                    this.token = token;
                    return getDatabases();
        });

        dbs.onSuccess( list -> System.out.println(list.toString()) )
        .onFailure( error -> System.out.print(error.toString()) );
//        // make sure our database exists, then get the first event
//        Future<JsonObject> event = dbs.compose( list -> {
//            String db = config.getString("db");
//            if (!list.contains(db))
//                 new Exception( "Missing database '"+db+"', "+list.toString());
//
//            System.out.println( list.toString() );
//
//            // TODO get the first event here!
//        }, cause -> {});

//            // // TODO determine the end time?
//            // // let end = await getDocument( start.total_rows-1 );
//        });
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

    /** https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs
     * @returnasdf An array of available databases as specified in CouchDB API. */
    private Future<JsonArray> getDatabases() {
        Promise<JsonArray> promise = Promise.promise();

        // craft an Http request for the couch api endpoint
        String host = config().getString("host");
        int port = config().getInteger("port");
        String cookie = "AuthSession=" + token.getString("AuthSession");
        HttpRequest<JsonArray> dbs = client.get(port, host, "/_all_dbs")
                .putHeader("Accept", "application/json")
                .putHeader("Cookie", cookie)
                .expect(ResponsePredicate.status(200, 299))
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonArray());

        // send it asynchronously
        dbs.send( request -> {
            if (request.succeeded()) {
                HttpResponse<JsonArray> response = request.result();
                printResponse(response);
                promise.complete(response.body());
            } else
                promise.fail( request.cause() );
        });
        return promise.future();
    }

    /** Get the 'i'th document in key order from the database. */
    private Future<JsonObject> getDocument( int index ) {

        return Future.future( promise -> {

            // assemble the URI and arguments for the specified page

            // fetch the results
            String uri = "http://" +
                    config().getString("host") +
                    ':' + config().getString("port") +
                    '/' + config().getString("db") +
                    "/_design/" + config().getString("design") +
                    "/_view/" + config().getString("view") +
                    "?include_docs=true&limit=1&skip=" + index;
            HttpRequest<JsonObject> getDoc = client.get(uri)
                    .putHeader("Accept", "application/json")
                    .putHeader("AuthSession", token.getString("AuthSession"))
                    .expect(ResponsePredicate.status(200, 299))
                    .expect(ResponsePredicate.JSON)
                    .as(BodyCodec.jsonObject());

            getDoc.send(request -> {
                if (!request.succeeded())
                    promise.fail( request.cause() );

                HttpResponse<JsonObject> response = request.result();
                promise.complete( response.body() );



            });
        });
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
        token.forEach(entry->System.out.println(entry.getKey()+"="+entry.getValue().toString()));
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
