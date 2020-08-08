package server.learn;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

/** Sketching out authorization stuff.
 * Problems that come to mind
 * - how do I enable SSL? Need to install OpenSSL on our containers?
 * - Even inside Docker it is good practice to use SSL between containers
 * - Need to look into a more modern authentication scheme, like OAuth2
 * */
public class AuthVerticle extends AbstractVerticle {

    WebClient client;

    MultiMap form = MultiMap.caseInsensitiveMultiMap()
            .set("name", "admin")
            .set("password", "password");

    public void start(Promise<Void> startPromise) {
        WebClientOptions options = new WebClientOptions()
                .setSsl(true)
//                .setSslEngineOptions(new OpenSSLEngineOptions()) // how do I install this in images...
//                .setPemKeyCertOptions(new PemKeyCertOptions().
//                        setKeyPath("./couchdb/cert/privkey.pem").
//                        setCertPath("./couchdb/cert/couchdb.pem"))
//                .setTrustAll(true)
                ;
        client = WebClient.create(vertx, options);

        HttpRequest<Buffer> getSession = client.post(5984, "localhost", "/_session")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .putHeader("Content-Length", "28")
                .expect(ResponsePredicate.SC_SUCCESS)
//                .basicAuthentication("admin", "password") // only want to use this if we have https set up
//                .bearerTokenAuthentication(); // TODO for OAuth2...
                .ssl(true);

        getSession.sendForm(form, request-> {
            // print out request info
            System.out.println("HEADERS:");
            getSession.headers().forEach( header->{
                System.out.println( header.getKey()+'='+header.getValue() );
            });
            System.out.println("BODY:");
            System.out.println( form );

            if (request.succeeded()) {

                // print out response info
                HttpResponse<Buffer> response = request.result();
                System.out.println("STATUS:"+response.statusCode()+"="+response.statusMessage());
                System.out.println("HEADERS:");
                response.headers().forEach( header->{
                    System.out.println( header.getKey()+'='+header.getValue() );
                });
                System.out.println("COOKIES:");
                response.cookies().forEach( System.out::println );
                System.out.println("BODY:");
                System.out.println( response.bodyAsString() );

                response.trailers().forEach( System.out::println );
            } else {
                System.err.println(request.cause().getMessage());
                request.cause().printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new AuthVerticle());
    }
}
