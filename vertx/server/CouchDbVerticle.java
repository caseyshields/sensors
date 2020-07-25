package server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.client.predicate.ResponsePredicateResult;
import io.vertx.ext.web.codec.BodyCodec;

import java.util.function.Function;

/** So a Vertx client is already written for MongoDB... */
public class CouchDbVerticle extends AbstractVerticle {

    WebClient client;

    JsonObject credentials = new JsonObject()
            .put("name", "admin")
            .put("password", "password");

    public void start(Promise<Void> startPromise) {
        client = WebClient.create(vertx);
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
}
