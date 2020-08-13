package server;

import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import server.couch.CouchClient;

public class CaveServer extends AbstractVerticle {

    CouchClient couchdb;

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
        this.couchdb = new CouchClient( vertx,
                config().getString("host"),
                config().getInteger("port") );
        JsonObject cred = config().getJsonObject("credentials");
        couchdb.getSession(
                cred.getString("name"),
                cred.getString("password") )
        .onSuccess( token -> { // TODO really the user should log in with their own credentials.

            // Build the routes if we can authenticate
            router.route()
                    .path("/api/missions")
                    .handler( this::getMissions );
            router.route()
                    .path("/api/mission/:mission")
                    .handler( this::getProducts );
            router.route()
                    .path("/api/mission/:mission/product/:product")
                    .handler( this::getEvents );

            // pass every other get request to the static handler
            StaticHandler handler = StaticHandler.create()
                    .setWebRoot("./web/")
                    .setIncludeHidden(false)
                    .setFilesReadOnly(false);
            router.route().method(HttpMethod.GET).handler(handler);
            server.requestHandler(router).listen(43210); // TODO put this in the configuration

            promise.complete();
        })

        // TODO add a Error page with Admin/Developer contacts
        .onFailure(promise::fail);
    }

    public void getMissions( RoutingContext context ) {
        // form a response
        HttpServerResponse response = context.response();
        response.putHeader("content-type", "Application/json");

        // get a list of missions and send it to the client
        couchdb.getMissions().onSuccess( json -> response.end(json.toString()) )

        // or tell the client what went wrong.
        .onFailure( error -> {
            JsonObject message = new JsonObject()
                    .put("type", error.getCause().getClass().getName())
                    .put("message", error.getMessage());
            response.end(message.toString());

//            context.fail( error );
        }); // TODO prob really shouldn't tell the outside world what's going on in here...
    }

    public void getProducts( RoutingContext context ) {
        HttpServerResponse response = context.response();
        response.putHeader("content-type", "Application/json");

        HttpServerRequest request = context.request();
        String umi = request.getParam("mission");

        couchdb.getProducts( umi )
        .onSuccess( json -> response.end(json.toString()) )
        .onFailure( error -> {
            JsonObject message = new JsonObject()
                    .put("type", error.getCause().getClass().getName())
                    .put("message", error.getMessage());
            response.end(message.toString());
        });
    }

    public void getEvents( RoutingContext context ) {
        HttpServerResponse response = context.response();
        response.putHeader( "content-type", "Application/json");

        HttpServerRequest request = context.request();
        String umi = request.getParam("mission");
        String product = request.getParam("product");

        couchdb.getEvents( umi, product )
        .onSuccess( json -> response.end(json.toString()) )
        .onFailure( error -> {
            JsonObject message = new JsonObject()
                    .put("type", error.getCause().getClass().getName())
                    .put("message", error.getMessage());
            response.end(message.toString());
        });

    }

    public void stop(Promise<Void> promise) {
        this.couchdb.close()
                .onSuccess( promise::complete )
                .onFailure( promise::fail );
    }
}
