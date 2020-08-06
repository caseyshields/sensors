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
        vertx.deployVerticle(new CouchDbVerticle(), options);
    }

    public void start(Promise<Void> promise) {

        //
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        // add api handlers first
        Route route = router.route().path("/api/*");
        route.handler( context -> {
            Map<String, String> params = context.pathParams();
            HttpServerResponse response = context.response();
            response.putHeader("content-type", "text/plain" );
            response.end("huh?");
        });

        // every other get request pass to the static handler
        StaticHandler handler = StaticHandler.create()
                .setWebRoot("./web/")
                .setIncludeHidden(false)
                .setFilesReadOnly(false);
        router.route().method(HttpMethod.GET).handler(handler);

        server.requestHandler(router).listen(8080);

//        // start up the CouchDB service
//        DeploymentOptions options = new DeploymentOptions()
//                //.setWorker( true )
//                .setConfig( config() );
//        Vertx vertx = Vertx.vertx();
//        vertx.deployVerticle(new CouchDbVerticle(), options);
//        // great. now how do I use it? messages? mapping routes? eh?

//        // create a test mission;
//        Future<Void> mission = session.compose(token-> {
//            return createMission("test");
//        });
//
//        Future<Void> info = mission.compose( woid -> {
//            //return getMission( "test" );
//            return addProduct("test", "network");
//        }).onSuccess( System.out::println );

//        // get all available databases
//        Future<JsonArray> databases = session.compose( token -> {
//            return getDatabases();
//        });
//
//        // make sure the database exists
//        Future<JsonObject> first = databases.compose( list -> {
//                    String db = config().getString("db");
//                    if (!list.contains(db))
//                        promise.fail("Missing database '" + db + "', " + list.toString());
//
//                    // then get the first event
//                    return getDocument(1);
//                    // TODO we can't really do this until we sort out views
//        });
//
//        first.onSuccess( json -> {
//
//            // TODO instead get the database information?
//            System.out.println( json.toString() );
//
//            // TODO get the end of the interval as well?
//            // let end = await getDocument( start.total_rows-1 );
//
//        }).onFailure( promise::fail );
    }


}
