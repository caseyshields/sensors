package server;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Map;

public class CaveServer extends AbstractVerticle {

    CouchDb couchdb;

    public static void main(String[] args) {
        JsonObject config = new JsonObject()
                .put("host", "localhost")
                .put("port", 5984)
                .put("db", "sensors")
                .put("credentials", new JsonObject()
                        .put("name", "admin")
                        .put("password", "Preceptor"));

        DeploymentOptions options = new DeploymentOptions()
                .setConfig( config );
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new CaveServer(), options);
    }

    public void start(Promise<Void> promise) {

        this.couchdb = new CouchDb( vertx,
                config().getString("host"),
                config().getInteger("port") );
        JsonObject cred = config().getJsonObject("credentials");
        couchdb.getSession(
                cred.getString("name"),
                cred.getString("password") )
            .onSuccess( token -> {

                //
                HttpServer server = vertx.createHttpServer();
                Router router = Router.router(vertx);

                // add api handlers first
                Route route = router.route().path("/api/*");
                route.handler(context -> {
                    Map<String, String> params = context.pathParams();
                    HttpServerResponse response = context.response();
                    response.putHeader("content-type", "text/plain");
                    response.end("huh?");
                });

                // every other get request pass to the static handler
                StaticHandler handler = StaticHandler.create()
                        .setWebRoot("./web/")
                        .setIncludeHidden(false)
                        .setFilesReadOnly(false);
                router.route().method(HttpMethod.GET).handler(handler);

                server.requestHandler(router).listen(43210);
            });
    }

    public void stop(Promise<Void> promise) {
        this.couchdb.close()
                .onSuccess( promise::complete )
                .onFailure( promise::fail );
    }
}
