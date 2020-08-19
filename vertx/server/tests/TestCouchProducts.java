package server.tests;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.Couch;
import server.couch.Database;
import server.couch.designs.network.Network;

public class TestCouchProducts {

    public static final String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb_products");

        // create a client to test database operations
        suite.before( context -> {
            Async async = context.async();
            Vertx vertx = Vertx.vertx();
            context.put("vertx", vertx);

            // get the session token from the database
            Couch client = new Couch( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor").compose( token -> {

                // cache the client for subsequest test requests
                context.put("client", client);

                // then create a test database for the products to be tested on
                return client.putDatabase( TEST_MISSION );

            }).onSuccess( mission-> {

                // cache the mission in the test context
                context.put("mission", mission);

                async.complete();
            } )
            .onFailure( context::fail );
        });

        suite.test("ProductCrud", context -> {
            Async result = context.async();
            Couch client = context.get("client");
            Database mission = context.get("mission");

            Network network = new Network();

            network.getDesignDocument().compose( ddoc -> {
                return mission.putDesign( network.getName(), ddoc );
            }).onSuccess( design -> {

                // get the products design doc from configuration and Couchdb
                Future<JsonObject> config = network.getDesignDocument();
                Future<JsonObject> couch = design.getDesignDocument();

                // once you have them both
                CompositeFuture.all( config, couch ).onSuccess( ar -> {

                    // make sure the view scripts are identical
                    JsonObject config_ddoc = ar.resultAt(0);
                    JsonObject couch_ddoc = ar.resultAt(1);
                    JsonObject config_view = config_ddoc.getJsonObject("views");
                    JsonObject couch_view = couch_ddoc.getJsonObject("views");
                    context.assertEquals( config_view.toString(), couch_view.toString() );

                    // TODO test delete

                    // TODO test list to make sure it is no longer there

                    result.complete();
                });
            });
        } );

        // close the session token after we're done with our tests
        suite.after( context -> {
            Async async = context.async();
            Couch client = context.get("client");

            // delete the test mission database, this will also delete all design documents
            client.deleteDatabase( TEST_MISSION ).onComplete(msg -> {

                // delete the user session
                client.deleteSession()
                        .onSuccess( v -> async.complete())
                        .onFailure( context::fail );

            }).onFailure( context::fail );
        });

        // just write the results to the console for right now
        suite.run(
                new TestOptions().addReporter(
                        new ReportOptions().setTo("console")));
    }
}
