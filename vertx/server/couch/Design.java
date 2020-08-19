package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;

/** A client for CouchDB Design Documents */
public class Design {

    Couch client;
    String db;
    String name;

    public Design(Couch client, String db, String name) {
        this.client = client;
        this.db = db;
        this.name = name;
    }

    public String getName() { return name; }

    // todo provide composeable methods for assembling scripts into a design document?

    /** Creates a design doc and adds it to the database
     * @param design produces the actual JSON design document
     * @return a future that completes when the design is created and fails otherwise */
    public Future<Void> create(String name, JsonObject design) {
        Promise<Void> promise = Promise.promise();

        String uri = "/" + db + "/_design/" + name;
        client.request(HttpMethod.PUT, uri)
                .as(BodyCodec.jsonObject())
                .sendJsonObject(design, request -> {

                    if (!request.succeeded())
                        promise.fail(request.cause());

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject message = response.body();

                    if (message.containsKey("error"))
                        promise.fail(message.toString());

                    promise.complete();
                });

        return promise.future();
    }

    /** Retrieves the design document. */
    public Future<JsonObject> getDesignDocument() {
        Promise<JsonObject> promise = Promise.promise();

        String uri = "/" + db + "/_design/" + name;
        client.request(HttpMethod.GET, uri)
                .as( BodyCodec.jsonObject() )
                .send( request -> {

                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();

                    promise.complete( body );
                });
        return promise.future();
    } // TODO add a variant that uses a HTTP Head command for 'has' predicates...

    public View getView(String name) {
        View view = new View(client, db, this.name, name);
        // todo do we verify the view exists in the database?
        return view;
    }
}
