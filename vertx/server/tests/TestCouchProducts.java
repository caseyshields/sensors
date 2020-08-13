package server.tests;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.CouchClient;
import server.couch.CouchProduct;

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
                client.createMission( TEST_MISSION ).onSuccess( v-> {
                    async.complete();
                }).onFailure( context::fail );

            }).onFailure( context::fail );
        });

        // make sure we can retrieve the mission list
        suite.test("ProductCrud", context -> {
            CouchClient client = context.get("client");
            Async result = context.async();
            Vertx vertx = context.get("vertx");

//            CouchProduct network = CouchProduct.Network();
//            String name = network.getName();
//            network.createDesignDocument(vertx).onSuccess( design -> {
//
//                // make sure the design document has the default event view
//                design.getJsonArray("views").
//
//                client.addProduct(TEST_MISSION, "network", design)
//                .onSuccess( json -> {
//
//                    // TODO what should the response look like?
//                    System.out.println(json.toString());
//
//                    // TODO get a list of products and make sure it includes the one we just made...
                    result.complete();
//
//                } ).onFailure( context::fail );
//            }).onFailure( context::fail );

        } );

        // close the session token after we're done with our tests
        suite.after( context -> {
            Async async = context.async();
            CouchClient client = context.get("client");

            // delete the test mission database
            client.deleteMission(TEST_MISSION).onComplete( msg -> {

                // delete the user session
                client.deleteSession().onSuccess( v -> {
                    async.complete();
                }).onFailure( context::fail );

            }).onFailure( context::fail );
        });

        // just write the results to the console for right now
        suite.run(
                new TestOptions().addReporter(
                        new ReportOptions().setTo("console")));
    }
}
