package server;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.client.predicate.ResponsePredicateResult;
import io.vertx.ext.web.codec.BodyCodec;

import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

/** So a Vertx client is already written for MongoDB...
 * might want to just switch to that rather than trying to write our own interface.
 * then again, will have to figure something out for interoperability with dolphin,
 * the new simulator, and we have to figure out queries and view over again...*/
public class CouchDbVerticle extends AbstractVerticle {

    JsonObject credentials = new JsonObject()
            .put("name", "admin")
            .put("password", "password");

    HashMap<String, String> token;

    /** Obtain a session cookie then delete it;
     * https://docs.couchdb.org/en/stable/api/server/authn.html#cookie-authentication */
    public void start(Promise<Void> promise) {
        WebClient client = WebClient.create(vertx);

        HttpRequest<JsonObject> session = client.post(5984, "localhost", "/_session")
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .putHeader("Content-Length", Integer.toString(credentials.toString().length()))
                .expect(ResponsePredicate.status(200, 299))
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject());

        session.sendJsonObject(credentials, request -> {

            if (!request.succeeded())
                promise.fail( request.cause() );
            HttpResponse<JsonObject> response = request.result();

            // make sure we have admin role
            JsonObject body = response.body();
            if (!body.getBoolean("ok"))
                promise.fail( body.getString("error") );
            if (!body.getJsonArray("roles").contains("_admin"))
                promise.fail( "not an administrator" );

            // save the session cookie
            String cookie = response.getHeader("Set-Cookie");
            this.token = parseCookie( cookie );

            ///
            HttpRequest<JsonObject> delete = client.delete(5984, "localhost", "/_session")
                    .putHeader("Accept", "application/json")
                    .putHeader("AuthSession", token.get("AuthSession") )
                    .expect(ResponsePredicate.status(200, 299))
                    .expect(ResponsePredicate.JSON)
                    .as(BodyCodec.jsonObject());
            delete.send( drequest-> {
                if (!drequest.succeeded())
                    promise.fail( drequest.cause() );
                HttpResponse<JsonObject> dresponse = drequest.result();
                JsonObject dbody = dresponse.body();
                if (!dbody.getBoolean("ok"))
                    promise.fail( dbody.getString("error") );
                System.out.println(dbody);
                promise.complete();
            });
            ///

//            promise.complete();
        });
    }

    HashMap<String, String> parseCookie(String cookie) {
        String entries[] = cookie.split(";");
        HashMap<String, String> map = new HashMap<>();
        Arrays.stream(entries).forEach(entry->{
            String pair[] = entry.split("=");
            if (pair.length==2)
                map.put(pair[0].trim(), pair[1].trim());
            else if (pair.length==1)
                map.put(pair[0].trim(), "");
        });
        map.forEach( (key,value)->System.out.println(key+"="+value) );
        return map;
    }

    // you can make your own response predicates...
//    Function<HttpResponse<Void>, ResponsePredicateResult> methodsPredicate = resp -> {
//        String methods = resp.getHeader("Access-Control-Allow-Methods");
//        if (methods != null) {
//            if (methods.contains("POST")) {
//                return ResponsePredicateResult.success();
//            }
//        }
//        return ResponsePredicateResult.failure("Does not work");
//    };

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new CouchDbVerticle());
    }

    public void test(Promise<Void> startPromise){
        WebClient client = WebClient.create(vertx);
        HttpRequest<JsonObject> getSession = client.post(5984, "localhost", "/_session")
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "application/json")
                .putHeader("Content-Length", Integer.toString(credentials.toString().length()) )
//                .addQueryParam("name","value" )
                .expect(ResponsePredicate.JSON)
                .as(BodyCodec.jsonObject())
//                .basicAuthentication("admin", "password") // only want to use this if we have https set up
                ;

        getSession.sendJsonObject(credentials, request -> {

            // print out request info
            System.out.println("HEADERS:");
            getSession.headers().forEach( header->{
                System.out.println( header.getKey()+'='+header.getValue() );
            });
            System.out.println("BODY:");
            System.out.println( credentials );
            System.out.println();

            if (request.succeeded()) {

                // print out response info
                HttpResponse<JsonObject> response = request.result();
                System.out.println("STATUS:"+response.statusCode()+"="+response.statusMessage());
                System.out.println("HEADERS:");
                response.headers().forEach( header->{
                    System.out.println( header.getKey()+'='+header.getValue() );
                });
                System.out.println("COOKIES:");
                response.cookies().forEach( cookie -> System.out.println(cookie) );
                System.out.println("BODY:");
                System.out.println( response.body() );

                response.trailers().forEach( trailer->{System.out.println(trailer);} );
            } else {
                System.err.println(request.cause().getMessage());
                request.cause().printStackTrace();
            }
        });
    }
}
