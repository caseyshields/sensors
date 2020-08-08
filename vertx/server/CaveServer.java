package server;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

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

        // Create the server
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // connect to couchdb and obtain a token
        this.couchdb = new CouchDb(
                config().getString("host"),
                config().getInteger("port") );
        JsonObject cred = config().getJsonObject("credentials");
        couchdb.getSession(
                cred.getString("name"),
                cred.getString("password") )

        // we only add api endpoints if we can authenticate...
        .onSuccess( token -> {
            // TODO really the user should log in with their own credentials.

            // add mission endpoint
            Route missions = router.route().path("/api/missions");
            missions.handler(context -> {

                // form a response
                HttpServerResponse response = context.response();
                response.putHeader("content-type", "Application/json");

                // get a list of missions and send it to the client
                couchdb.getDatabases()
                .onSuccess(json -> {
                    response.end(json.toString());
                    // TODO remove all non mission dbs...
                })

                // or tell the client what went wrong.
                .onFailure(error -> {
                    JsonObject message = new JsonObject()
                            .put("type", error.getCause().getClass().getName())
                            .put("message", error.getMessage());
                    response.end(message.toString());
                }); // TODO prob really shouldn't tell the outside world what's going on in here...
            });
            promise.complete();
        })
        .onFailure( error -> promise.fail(error) );

        // pass every other get request to the static handler
        StaticHandler handler = StaticHandler.create()
                .setWebRoot("./web/")
                .setIncludeHidden(false)
                .setFilesReadOnly(false);
        router.route().method(HttpMethod.GET).handler(handler);
        server.requestHandler(router).listen(43210); // TODO put this in the configuration
    }

    public void stop(Promise<Void> promise) {
        this.couchdb.close()
                .onSuccess( promise::complete )
                .onFailure( promise::fail );
    }
}
