package server.couch.designs;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

/** Represents a data product */
public interface Design {

    /** The view will be indexed by this name in the design document */
    String getName();

    /** Asynchronously construct a design document.
     * Typically assembled from some configuration files or databases... */
    Future<JsonObject> getDesignDocument();

    // TODO do we want one product to have multiple views? How would we describe the interface?
//    String[] getViews();
    // for example; Say we have a visualization with an analysis metric displayed in the top corner.
    // we will always need the chronological event view, as well as the MOP's view
    // if they are always used together, why put them in a separate design doc?
}
