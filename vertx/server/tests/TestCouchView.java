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
import server.couch.designs.Design;
import server.couch.designs.network.Network;

import java.util.ArrayList;
import java.util.List;

/** Test event creation, and retrieve them using a product view query */
public class TestCouchView {

    public static String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb_events");

        suite.before( context -> {
            Async async = context.async();

            Vertx vertx = Vertx.vertx();
            context.put("vertx", vertx);

            Design design = new Network();
            context.put( "design", design );

            // get the session token from the database and cache it
            Couch client = new Couch( vertx,"localhost", 5984);
            client.getSession("admin","Preceptor").compose( token -> {
                context.put("client", client);

                // then create a test database for the products to be tested on
                return client.putDatabase( TEST_MISSION );

            }).compose( mission -> {
                context.put("mission", mission);

                // then create a product view to test out
                return mission.putView( design );

            }).onSuccess( view -> {
                context.put("product", view);
                async.complete();
            } )
            .onFailure( context::fail );
        });

        suite.test( "event_crud", context -> {
            Async async = context.async();
            Couch client = context.get("client");
            Database mission = context.get("mission");
            View product = context.get("product");
            Design design = context.get("design");

            // add a hundred test events to the mission database
            List<Future> events = new ArrayList<Future>();
            for (int n = 0; n<100; n++) {
                String stamp = String.format("%05d", n*100);
                String source = "sim";
                String id = stamp+"-"+source;
                JsonObject event = new JsonObject()
//                        .put("_id", "sim"+n)
                        .put("time", n*100)
                        .put("stamp", stamp)
                        .put("source", source)
                        .put("target", "test")
                        .put("class", "strobe")
                        .put("sic", "a")
                        .put("tap", "b")
                        .put("angle", n);
                events.add(
                        mission.putDoc( id, event)
                        .onSuccess( json -> {
                            // make sure the non-couch fields of the events match
                            context.assertEquals( json.getString("id"), id );
                            context.assertEquals( json.getString("value"), event.getString("value") );
                            context.assertNotNull( json.getString("rev") );
                        })
                );
            }

            CompositeFuture.all( events )
                .compose( v->{
                    String start = "[\"00100\",\"sim\"]";
                    String stop = "[\"01000\",\"sim\"]";
                    return product.getDocs(start, 10);
                } )
                .onSuccess( json -> {

//                    System.out.println(json.encodePrettily());

                    // make sure there are a hundred events in the database
                    context.assertEquals( json.getInteger("total_rows"), 100);

                    // make sure the interval starts at the second element
                    context.assertEquals( json.getInteger("offset"), 1);

                    // make sure there are 10 events in the specified interval
                    JsonArray rows = json.getJsonArray("rows");
                    context.assertEquals( rows.size(), 10 );

                    // meh, this isn't really an exhaustive test more of just a sanity check...

                    async.complete();
                })
                .onFailure( context::fail );
        } );

        // TODO add a test for accessing a Product's view of events...

        // delete the test mission database, then the client
        suite.after( context -> {
            Async async = context.async();
            Couch client = context.get("client");

            // deleting the database also deletes all it's documents
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
