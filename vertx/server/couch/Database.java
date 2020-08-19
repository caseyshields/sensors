package server.couch;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.codec.BodyCodec;
import server.couch.designs.Design;

/** Represents a database in CouchDB and provides operations for documents and views.
 * @author casey */
public class Database {

    Couch client;
    String db;

    /** Creates a Database client, without actually checking CouchDB for a corresponding database.
     * @param client The client which will perform all http requests
     * @param db the name of the database */
    public Database(Couch client, String db) {
        this.client = client;
        this.db = db;
    }

    public Couch getClient() { return client; }

    public String getName() { return db; }

    /** Add an event to the Mission database.
     * @param id the key used to index the document. It is important that the id is unique for the entire database.
     *           Otherwise subsequent inserts will generate update confilicts
     * @param doc the document to be added to the database */
    public Future<JsonObject> putDoc(String id, JsonObject doc) {
        Promise<JsonObject> promise = Promise.promise();

        client.request(HttpMethod.PUT, "/"+ db +"/"+id)
                .as(BodyCodec.jsonObject())
                .sendJsonObject( doc, request -> {

                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();
                    if (body.containsKey("error"))
                        promise.fail( body.toString() );
                    else
                        promise.complete(body);
                });
        return promise.future();
    }

    /** Get a specific document by it's id.
     * https://docs.couchdb.org/en/stable/api/document/common.html#get--db-docid
     * @param id the key provided when the document was added to the database
     * @return The document with the requested id in a JsonObject. */
    public Future<JsonObject> getDoc(String id) {
        Promise<JsonObject> promise = Promise.promise();
        client.request(HttpMethod.GET, "/"+ db +"/"+id)
                .as(BodyCodec.jsonObject())
                .send( request -> {

                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject body = response.body();
                    if (body.containsKey("error"))
                        promise.fail( body.toString() );
                    else
                        promise.complete( body );
                });
        return promise.future();
    }

    /** Fetch events from the default view.
     * @param startkey the lowest key in lexographic order, inclusive
     * @param endkey the highest key in lexographic order, inclusive*/
    public Future<JsonObject> getDocs(String startkey, String endkey) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        client.request(HttpMethod.GET, '/' + db + "/_all_docs")
                .addQueryParam( "include_docs", "true")
                .addQueryParam("startkey", '"'+startkey+'"')
                .addQueryParam("endkey", '"'+endkey+'"' )
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject json = response.body();

                    if (json.containsKey("error"))
                        promise.fail( json.toString() );

                    promise.complete( json );
                });

        return promise.future();
    }

    /** Fetch events from the default view
     * @param start lowest key in lexographic order
     * @param limit the maximum number of documents to retrieve */
    public Future<JsonObject> getDocs(String start, Integer limit) {
        Promise<JsonObject> promise = Promise.promise();

        // assemble the URI and arguments for the specified page
        client.request(HttpMethod.GET, '/' + db + "/_all_docs")
                .addQueryParam( "include_docs", "true")
                .addQueryParam("startkey", '"'+start+'"')
                .addQueryParam("limit", limit.toString() )
                .as(BodyCodec.jsonObject())
                .send(request -> {
                    if (!request.succeeded())
                        promise.fail( request.cause() );

                    HttpResponse<JsonObject> response = request.result();
                    JsonObject json = response.body();

                    if (json.containsKey("error"))
                        promise.fail( json.toString() );

                    promise.complete( json );
                });

        return promise.future();
    } // TODO should I just return an array with the documents, stripping out the redundant view index info?

    // todo add methods for document update and delete?

    /** Get a list of all available views of the database
     * @return a JsonArray containing the names of all design documents added to this database */
    public Future<JsonArray> getViews() {
        Promise<JsonArray> promise = Promise.promise();
        client.request( HttpMethod.GET, "/"+ db +"/_design_docs")
                .as(BodyCodec.jsonObject())
                .send( request -> {
                    if (request.succeeded()) {
                        HttpResponse<JsonObject> response = request.result();
                        JsonObject body = response.body();
                        JsonArray products = new JsonArray();

                        // get the list of design documents
                        JsonArray rows = body.getJsonArray("rows");
                        rows.forEach( row -> {

                            // trim the conventional design document prefix
                            String id = ((JsonObject)row).getString("id");
                            String view = id.substring( 1 + id.lastIndexOf("/") );

                            // the views correspond to data products
                            products.add( view );
                        });
                        promise.complete(products);
                    } else
                        promise.fail( request.cause() );
                } );
        return promise.future();
    }

    /** Creates a view object without consulting the database
     * @param design the name of the design document, it should match an already existing document.
     * @return a View object for the created design */
    public View getView(String design) {
        return new View(client, db, design);
    }

    /** Creates a design doc and adds it to the database
     * @param design produces the actual JSON design document
     * @return a View object for the created design */
    public Future<View> putView(Design design) {
        Promise<View> promise = Promise.promise();

        design.getDesignDocument().onSuccess( document -> {

            String uri = "/" + db + "/_design/" + design.getName();
            client.request(HttpMethod.PUT, uri)
            .as(BodyCodec.jsonObject())
            .sendJsonObject(document, request -> {

                if (!request.succeeded())
                    promise.fail(request.cause());

                HttpResponse<JsonObject> response = request.result();
                JsonObject message = response.body();

                if (message.containsKey("error"))
                    promise.fail(message.toString());

                View view = new View(client, db, design.getName());
                promise.complete( view );
            });

        }).onFailure( promise::fail );

        return promise.future();
    }
}
