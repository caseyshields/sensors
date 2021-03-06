package server.tests;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.Couch;
import server.couch.Database;
import server.couch.View;
import server.couch.designs.network.Network;

import java.util.ArrayList;
import java.util.List;

/** Test event creation and retrieval using only the default view. */
public class TestCouchEvents {

    public static String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb_events");

        suite.before( context -> {
            Async async = context.async();

            Vertx vertx = Vertx.vertx();
            context.put("vertx", vertx);

            Network design = new Network();
            context.put( "design", design );

            // get the session token from the database
            Couch client = new Couch( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor")
            .compose( token -> {

                // cache the client for subsequest test requests
                context.put("client", client);

                // then create a test database for the products to be tested on
                return client.putDatabase( TEST_MISSION );

                //TODO add a product so we can test views as well...
            })
            .onSuccess( db -> {
                // cache the database that was created
                context.put("db", db);

                async.complete();
            } )
            .onFailure( context::fail );
        });

        suite.test( "event_crud", context -> {
            Async async = context.async();
            Couch client = context.get("client");
            Database db = context.get("db");
            Network design = context.get("design");

            // add a hundred test events to the mission database
            List<Future> events = new ArrayList<Future>();
            for (int n = 0; n<100; n++) {

                // ordering is lexical so we need to pad numeric values
                String stamp = String.format("%05d", n*100);
                String source = "sim";
                String key = stamp+"-"+source;
                JsonObject event = new JsonObject()
                        .put("time", n*100)
                        .put("stamp", stamp)
                        .put("source", source)
                        .put("target", "test")
                        .put("class", "strobe")
                        .put("sic", "a")
                        .put("tap", "b")
                        .put("angle", n * 2.5 / Math.PI);
                events.add(
                        db.putDoc(key, event).onSuccess(json -> {
                            // make sure the non-couch fields of the events match
                            context.assertTrue( json.getBoolean("ok") );
                            context.assertEquals( json.getString("id"), key );
                            context.assertNotNull( json.getString("rev") );
                        })
                );
            }

            CompositeFuture.all( events )
                .compose( v->{
                    String start = "\"00100\"";
                    String stop = "\"01000-sim\"";
//                    return Events.get(client, TEST_MISSION, start, stop);
                    View view = db.getDefaultView();
                    return view.getDocs(start, 10);
                    // TODO maybe the test should be to make two equivalent queries and match them?
                } )
                .onSuccess( json -> {
                    System.out.println(json.encodePrettily());

                    // make sure there are a hundred events in the database
                    context.assertEquals( json.getInteger("total_rows"), 100);

                    // make sure the interval starts at the second element
                    context.assertEquals( json.getInteger("offset"), 1);

                    // make sure there are 10 events in the specified interval
                    JsonArray rows = json.getJsonArray("rows");
                    context.assertEquals( rows.size(), 10 );

                    // meh, this isn't really an exhaustive test more of just a sanity check...
                    async.complete(); })
                .onFailure( context::fail );
        } );

        // TODO add a test for accessing a Product's view of events...

        // delete the test mission database, then the client
        suite.after( context -> {
            Async async = context.async();
            Couch client = context.get("client");
            client.deleteDatabase(TEST_MISSION)
                .compose( v-> client.deleteSession() )
                .onSuccess( v-> async.complete() )
                .onFailure( context::fail );
        });

        // just write the results to the console for right now
        suite.run(
                new TestOptions().addReporter(
                        new ReportOptions().setTo("console")));
    }

    // a recursive approach...
//    private Future<JsonObject> simulate(CouchClient client, int n) {
//        JsonObject event =  new JsonObject()
//                .put("time", n)
//                .put("stamp", Integer.toString(n))
//                .put( "source", "sim" )
//                .put( "target", "test")
//                .put( "class", "strobe" )
//                .put( "sic", "a" )
//                .put( "tap", "b" )
//                .put( "angle", n*2.5/Math.PI );
//        Future<JsonObject> post = Events.post(client, TEST_MISSION, event);
//        if (n<100)
//            post.compose( json -> {
//                return simulate(client, n+1);
//            } );
//        return post;
//    }

}
