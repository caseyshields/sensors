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
import server.couch.Couch;
import server.couch.Database;
import server.couch.Design;
import server.couch.View;

public class CaveServer extends AbstractVerticle {

    Couch couchdb;

    /** todo technically a design document can have many views of different types. We'll cross that bridge when the need arises... */
    static final String DefaultView = "events";

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
        this.couchdb = new Couch( vertx,
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
                    .path("/api/mission/:mission/event/:event")
                    .handler( this::getEvent );
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
        couchdb.getDatabases().onSuccess(json -> response.end(json.toString()) )

        // or tell the client what went wrong.
        .onFailure( error -> response.end(error.getMessage()) ); // context.fail( error );
        // TODO prob really shouldn't tell the outside world what's going on in here...
    }

    public void getProducts( RoutingContext context ) {
        HttpServerResponse response = context.response();
        response.putHeader("content-type", "Application/json");

        HttpServerRequest request = context.request();
        String umi = request.getParam("mission");

        Database mission = new Database(couchdb, umi);
        mission.getDesigns()
        .onSuccess( json -> response.end(json.toString()) )
        .onFailure( error -> response.end(error.getMessage()) );
    }

    public void getEvent( RoutingContext context ) {
        HttpServerResponse response = context.response();
        response.putHeader( "content-type", "Application/json");

        HttpServerRequest request = context.request();
        String umi = request.getParam("mission");
        String event = request.getParam("event");

        Database mission = new Database(couchdb, umi);
        mission.getDoc( event )
                .onSuccess( json -> response.end(json.toString()) )
                .onFailure( error -> response.end(error.getMessage()) );
    }

    public void getEvents( RoutingContext context ) {
        HttpServerResponse response = context.response();
        response.putHeader( "content-type", "Application/json");

        HttpServerRequest request = context.request();
        String umi = request.getParam("mission");
        String product = request.getParam("product");

        Database mission = new Database(couchdb, umi);
        Design design = new Design(couchdb, mission.getName(), product);
        View view = design.getView(DefaultView);
        view.getDocs( "\"\"", 10 )
        .onSuccess( json -> response.end(json.toString()) )
        .onFailure( error -> {
                response.end(error.getMessage());
        } );
    }

    public void stop(Promise<Void> promise) {
        this.couchdb.deleteSession()
                .onSuccess( promise::complete )
                .onFailure( promise::fail );
    }
}
