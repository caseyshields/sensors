/** A client interface to a WebSocket endpoint for streaming chronological data.
 * @param config.mission : A 16 character UMI string required by the DL system. It is of the format; 'YYYYJJJ-PPPP-SSS'
 * @param config.system : A system mnemonic */
export default async ( config ) => {

    // overwrite defaults with user supplied configuration
    let state = {
        uri : "http://localhost:43210/",
        mission : 'YYYJJJ-PPPP-SSS',
        system : 'system',
        user : 'guest',
        // start : ''
        // stop : ''
    }
    Object.assign(state, config);

    // an internal message counter for the client, which is used as a key for cached requests.
    let _sequence = 1;

    // a cache for client requests. used to track client requests while waiting on a server response
    let _requests = new Map();
    // TODO I'm pretty sure because of underlying TCP ordering guarantees a queue should be sufficient instead...

    // a client object whose methods are mapped to the streaming API
    let _client = null;

    // a websocket connection to the back end from which the client pulls data
    let _socket = new WebSocket( config.uri );

    // when the socket opens send the initialization message to server
    _socket.onopen = () => {
        _client = {
            open : ( config ) => _request( {type:"open", sequence:_sequence, state} ),

            step : ( interval ) => _request( { type:'step', sequence:_sequence, interval } ),

            seek : ( time ) => _request( {type:'seek', sequence:_sequence, time} ),

            say : ( stuff ) => _request( {type:'say', sequence:_sequence, stuff} ),

            close : ( mission, system ) => _request( {type:'close', sequence:_sequence, mission, system} )
        };
        return( _client );
    };

    // handle messages arriving from the server on the websocket
    _socket.onmessage = message => {
        try {
            // extract response from message
            let response = JSON.parse( message.data );

            //DEBUG
            // console.log( response );

            // make sure response corresponds to a user request
            if (!response.sequence)
                throw {error:'missing request sequence id'};

            // DEBUG calculate latency
            response.latency = d3.now() - response.latency;

            // retrieve the cached context
            let context = _requests.get( response.sequence );
            if (!context)
                throw {error:'missing request context'};
            // possible when server socket is closed while

            // invoke the failure callback if there was an error on the server
            if (response.hasOwnProperty('error')) //(response.error)
                context.failure( response );

            // otherwise invoke resolution callback with the response
            else
                context.callback( response );

            // TODO if an error has occurred after we connected,
        } catch (error) {
            // lets just burn this mother down right now. We need to have a plan if this is possible.
            console.log( error );
            _close( error ); // could this be a message pushed from the server instead of requested?
        }
    };

    _socket.onclose = () => {
        _close( 'socket closed' );
    };

    _socket.onerror = ( error ) => {
        // TODO I need to study this more;
        // can it be a temporary loss of connectivity that we can recover from?
        // or is it broken entirely and I should dispose of every cached request?

        // if the client is connected, shut everything down
        if (_client) _close( error );
        // TODO we might have to set an error state for subsequent requests or messages

        // otherwise we have a failed connection, so we have to reject the constructor promise
        else throw( error );
    };

    /** Wraps an asyncronous callback style server request in a Promise */
    function _request( message ) {
        // console.log( message ); //DEBUG
        return new Promise( (resolve, reject) => _send( message, resolve, reject ) );
    }

    /** Sends a command to the Cave server using an asynchronous callback for failure and success in the Node idiom */
    function _send( message, callback, failure ) {

        // increment the client's request counter
        message.sequence = _sequence++;
        if (_requests.has( _sequence )) {
            failure( 'sequence collision' );
        } // this should not be possible...

        // DEBUG instrumentation stuff, not sure what I'm looking for here
        message.pending = _requests.size;
        message.latency = d3.now();
        // if this doesn't fall to zero when paused and buffered, we have problems...

        // cache the context by the request sequence number
        _requests.set( message.sequence, {callback, failure} );

        // send the request to the Cave server
        _socket.send( JSON.stringify( message ) );

        // TODO do we need to set timeouts for these things?
        // Say we invoke failure after some configurable amount of time?
        // Do we clear the cache or document late arrivals?
        // Pretty sure we can just track the delta between the most recent completed request, without putting a bunch of timeout in the event loop.
    }

    /** Dispose of all connection resources */
    function _close( reason ) {
        // fail every cached request
        for (let sequence in _requests) {
            let context = _requests.get( sequence );
            context.failure( reason );
        }

        // clear the request cache
        _requests.clear();
        //any subsequent received message will just be logged and discarded.

        // close the socket, redundant calls have no effect
        _socket.close( 1000 ); // TODO use a more informative code...
        //DOMException: Failed to execute 'close' on 'WebSocket': The code must be either 1000, or between 3000 and 4999. 0 is neither.

        console.log( reason );
    }
}

// createClient( 'ws://localhost:43210/echo/' ).then( async (client)=>{
//     console.log( await client.suscribe( 'casey', 'test', {} ) );
//     console.log( await client.open('YYYJJJ-PPPP-SSS', 'system') );
//     console.log( await client.step( 1000 ) );
//     console.log( await client.step( 1000 ) );
//     console.log( await client.step( 1000 ) );
//     console.log( await client.step( 1000 ) );
//     console.log( await client.close('YYYJJJ-PPPP-SSS', 'system') );
// } ).catch( console.error );