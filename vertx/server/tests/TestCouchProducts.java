package server.tests;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.CouchClient;
import server.couch.Mission;
import server.couch.Product;
import server.couch.designs.Design;
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
            CouchClient client = new CouchClient( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor").onSuccess( token -> {

                // cache the client for subsequest test requests
                context.put("client", client);

                // then create a test database for the products to be tested on
                Mission.put(client, TEST_MISSION )
                        .onSuccess( v->async.complete() )
                        .onFailure( context::fail );

            }).onFailure( context::fail );
        });

        suite.test("ProductCrud", context -> {
            CouchClient client = context.get("client");
            Async result = context.async();

            Design design = new Network();


            // add the configuration to CouchDB
            Product.put(client, TEST_MISSION, design).onSuccess( v-> {

                // get the products design doc from configuration and Couchdb
                Future<JsonObject> config = design.getDesignDocument();
                Future<JsonObject> couch = Product.get(client, TEST_MISSION, design.getName());

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
            CouchClient client = context.get("client");

            // delete the test mission database
            Mission.delete(client, TEST_MISSION)
            .onComplete( msg -> {

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
