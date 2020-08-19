package server.tests;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.Couch;
import server.couch.Database;

public class TestCouchEvent {

    public static String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb_events");

        suite.before( context -> {
            Async async = context.async();

            Vertx vertx = Vertx.vertx();
            context.put("vertx", vertx);

            // get the session token from the database
            Couch client = new Couch( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor")
            .compose( token -> {

                // cache the client for subsequest test requests
                context.put("client", client);

                // then create a test database for the products to be tested on
                return client.putDatabase( TEST_MISSION );
            })
            .compose( db -> context.put("db", db) )
            .onSuccess( v->async.complete() )
            .onFailure( context::fail );
        });

        suite.test( "event_crud", context -> {
            Async async = context.async();
            Couch client = context.get("client");
            Database mission = context.get("mission");

            String stamp = "YYYY-MM-DDThh:mm:ss.sTZD";
            String source = "file:line";
            String id = stamp+"-"+source;
            JsonObject event = new JsonObject()
                    .put("stamp", stamp)
                    .put("source", source)
                    .put("value", "stuff and the like");
            // TODO make something generate a sequence so we can have meaningful view interval queries...
            // should I make a fake project or should I make another simulator for product 2 in Java?....

            // add an event to the test database
            mission.putDoc(id, event).compose(json -> {

                // make sure the id matches and we got some revision number
                context.assertEquals(json.getBoolean("ok"), true);
                context.assertNotNull(json.getString("rev")); // maybe cache this so we can test performing an update?

                // read the event from the test database
                return mission.getDoc(id);

            }).onSuccess( json -> {

                // make sure the non-couch fields of the events match
                context.assertEquals( json.getString("_id"), id );
                context.assertEquals( json.getString("value"), event.getString("value") );
                context.assertNotNull( json.getString("_rev") );

                async.complete();
            }).onFailure( context::fail );
        } );

        // TODO add a test for accessing a Product's view of events...

        // delete the test mission database, then the client
        suite.after( context -> {
            Async async = context.async();
            Couch client = context.get("client");
            client.deleteDatabase( TEST_MISSION )
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
