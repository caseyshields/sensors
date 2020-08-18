package server.tests;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.CouchClient;
import server.couch.Events;
import server.couch.Mission;

public class TestCouchEvent {

    public static String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb_events");

        suite.before( context -> {
            Async async = context.async();

            Vertx vertx = Vertx.vertx();
            context.put("vertx", vertx);

            // get the session token from the database
            CouchClient client = new CouchClient( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor")
            .compose( token -> {

                // cache the client for subsequest test requests
                context.put("client", client);

                // then create a test database for the products to be tested on
                return Mission.put(client, TEST_MISSION );

                //TODO add a product so we can test views as well...
            })
            .onSuccess( v->async.complete() )
            .onFailure( context::fail );
        });

        suite.test( "event_crud", context -> {
            Async async = context.async();
            CouchClient client = context.get("client");

            JsonObject event = new JsonObject()
                    .put("_id", "file:0,line:0")
                    .put("value", "stuff and the like");
            // TODO make something generate a sequence so we can have meaningful view interval queries...
            // should I make a fake project or should I make another simulator for product 2 in Java?....

            // add an event to the test database
            Events.post(client, TEST_MISSION, event).compose( json -> {

                // make sure the id matches and we got some revision number
                context.assertEquals(json.getBoolean("ok"), true);
                context.assertNotNull(json.getString("rev")); // maybe cache this so we can test performing an update?

                // read the event from the test database
                return Events.get(client, TEST_MISSION, "file:0,line:0");

            }).onSuccess( json -> {

                // make sure the non-couch fields of the events match
                context.assertEquals( json.getString("_id"), event.getString("_id") );
                context.assertEquals( json.getString("value"), event.getString("value") );
                context.assertNotNull( json.getString("_rev") );

                async.complete();
            }).onFailure( context::fail );
        } );

        // TODO add a test for accessing a Product's view of events...

        // delete the test mission database, then the client
        suite.after( context -> {
            Async async = context.async();
            CouchClient client = context.get("client");
            Mission.delete(client, TEST_MISSION)
                .compose( v-> client.deleteSession() )
                .onSuccess( v-> async.complete() )
                .onFailure( context::fail );
        });

        // just write the results to the console for right now
        suite.run(
                new TestOptions().addReporter(
                        new ReportOptions().setTo("console")));
    }
}
