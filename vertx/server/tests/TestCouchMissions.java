package server.tests;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import server.couch.CouchClient;
import server.couch.Mission;

public class TestCouchMissions {

    public static final String TEST_MISSION = "test_mission";

    public static void main(String[] args) {
        TestSuite suite = TestSuite.create("test_couchdb");

        // create a client to test database operations
        suite.before( context -> {
            Async async = context.async();
            Vertx vertx = Vertx.vertx();
            CouchClient client = new CouchClient( vertx,"localhost", 5984);

            // get the session token from the database
            client.getSession("admin","Preceptor")
            .onSuccess( token -> {
                context.put("client", client);
                async.complete();
            })
            .onFailure( context::fail );
        });

        // make sure we can retrieve the mission list
        suite.test("getMissions", context -> {
            CouchClient client = context.get("client");
            Async result = context.async();

            Mission.list(client)
//            client.getMissions()
            .onSuccess( json -> {
                String s = json.toString();
                context.assertTrue( (s.length()>0), s );
                result.complete();
            } )
            .onFailure( context::fail );

        } );

        // make sure we can create and remove a database
        suite.test( "missionCrud", context -> {
            CouchClient client = context.get("client");
            Async result = context.async();

            // add the mission to couch
            Mission.put(client, TEST_MISSION).compose( v -> {

                // then try to read it from couch
                return Mission.get(client, TEST_MISSION);

            }).compose( json -> {

                // make sure the database's summary object has reasonable values
                context.assertTrue( json.containsKey("db_name") );
                context.assertEquals( json.getString("db_name"), TEST_MISSION);
                context.assertTrue( json.containsKey("doc_count"));
                context.assertEquals( json.getLong("doc_count"), 0L);
                context.assertTrue( json.containsKey("doc_del_count"));
                context.assertEquals( json.getLong("doc_del_count"), 0L);

                // then try to delete the database
                return Mission.delete(client, TEST_MISSION);
            })
            .onSuccess( v-> result.complete() )
            .onFailure( context::fail);
        });
        // example CouchDB Summary document
//        {"db_name":"test_couchdb_client",
//        "purge_seq":"0-g1AAAABPeJzLYWBgYMpgTmHgzcvPy09JdcjLz8gvLskBCeexAEmGBiD1HwiyEhlwqEtkSKqHKMgCAIT2GV4",
//        "update_seq":"0-g1AAAABPeJzLYWBgYMpgTmHgzcvPy09JdcjLz8gvLskBCeexAEmGBiD1HwiyEhlwqEtkSKqHKMgCAIT2GV4",
//        "sizes":{"file":16692,"external":0,"active":0},
//        "props":{},
//        "doc_del_count":0,
//        "doc_count":0,
//        "disk_format_version":8,"compact_running":false,"cluster":{"q":2,"n":1,"w":1,"r":1},"instance_start_time":"0"}

        // close the session token after we're done with our tests
        suite.after( context -> {
            Async async = context.async();
            CouchClient client = context.get("client");

            client.deleteSession()
            .onSuccess( v->async.complete() )
            .onFailure( context::fail );
        });

        // just write the results to the console for right now
        suite.run(
                new TestOptions().addReporter(
                        new ReportOptions().setTo("console")));
    }
}
