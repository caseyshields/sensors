// TODO only kept for reference while developing the vertx client!
// there is not really a secure way to authenticate to CouchDB from a browser client over CORS.
// Really should have just stuck with conventional wisdom but I thought I could do some serverless, CouchApp kinda thing... oh well.

/** Provides a browser client interface to a CouchDB server.
 * 
 * On Windows the CouchDB service can be started/stopped using the net command;
 *  > net start "apache couchdb"
 * It can also be controlled using the included scripts'
 *  C:/CouchDB/bin> couchdb stop
 *  C:/CouchDB/bin> couchdb restart
 * 
 * Once started the Database can be manipulated using the Fauxton webapp at;
 *  <host:port>/_utils
 * Make sure that CORS is enabled for your domain using the settings tab.
 * 
 * @param {String} config.host - the CouchDB URL
 * @param {Number} config.port - the port of the CouchDB service
 * @param {String} config.db - the name of the database being read
*/
export default (config) => {
    // TODO Couch indexes might be a better way to do this than views...
    // then again views also provide the opportunity to transform the data in situ
    // which, as we've seen, can be fairly important when instrumentation throws us curve balls the night of the mission.

    // default design docs & view for data
    const design = 'stream';
    const view = 'chronological';

    // internal Client state
    let state = {
        host : 'localhost',
        port : 5984,
        db : 'sensors', // the test database made by the simulator
        time : null // current page in the stream
    }
    Object.assign(state, config);

    // Assemble a client object for the user
    return {open,step,seek,close};

    /** Checks that the Database which corresponds to the specified data exists and has data. */
    async function open(name, password) {

        // it now requires admin privileges
        // verify the requested database exists
        let dbs = await getDatabases();
        if (!dbs.includes(state.db))
            throw( {message: `Invalid db '${state.db}', available dbs ${JSON.stringify(dbs)}`} );

        // authorize

        // // let content = JSON.stringify( {name, password} );
        // let response = await fetch( '/_session', {
        //     method: 'POST',
        //     body: {name, password},
        //     headers: {
        //         'Content-Type': 'application/json',
        //         // 'Content-Length': content.length
        //     } }
        // );
        // return response;

        // determine the start time of the data by fetching the first document
        let document = await getDocument(0);
        if (!document.rows.length)
            throw ( {message: `The ${state.db} database does not contain any events`} );
        state.time = document.rows[0].key[0]; // the first field of the compound key is a timestamp

        // // TODO determine the end time? 
        // // let end = await getDocument( start.total_rows-1 );

        return ( {host:state.host, port:state.port, db:state.db, design, view, time:state.time} );
    }

    /** retrieves the next interval of events, and advances the current time. */
    async function step( interval ) {
        let end = getNext(state.time, interval);
        let docs = await getDocumentInterval( state.time, end );
        state.time = end;
        docs.done = docs.offset < docs.total_rows
        return docs;
    }

    /** sets the current time. */
    async function seek( to ) {
        state.time = to;
        //TODO actually try to read a record and return some data?
    }

    /** This client relies on fetch calls so there are no resources to close here really */
    async function close() {}

    /** Get all the documents in the time interval. */
    async function getDocumentInterval( startkey, endkey ) {
        // assemble the URI and arguments for the specified page
        let uri = `http://${state.host}:${state.port}/${state.db}/_design/${design}/_view/${view}?include_docs=true`;
        if (startkey) uri += `&start_key=[${startkey},""]`;
        if (endkey) uri += `&end_key=[${endkey},""]`;

        // fetch the results
        let response = await fetch(uri, {mode:'cors'});

        // check for non-network errors
        if (!response.ok)
            throw(response);

        // invoke the built in JSON reader for the response body
        let page = await response.json();
        return page;
    }// TODO we still probably want ot limit the number of documents, if not the total size...

    /** Get the 'i'th document in key order from the database. */
    async function getDocument( index ) {
        // assemble the URI and arguments for the specified page
        let uri = `http://${state.host}:${state.port}/${state.db}/_design/${design}/_view/${view}`;
        let args = `?include_docs=true&limit=1&skip=${index}`

        // fetch the results
        let response = await fetch(uri+args, {mode:'cors'});

        // check for non-network errors
        if (!response.ok)
            throw(response);

        // invoke the built in JSON reader for the response body
        let page = await response.json();
        return page;
    }

    /** Calculate the time after the given interval */
    function getNext( t, dt ) {
        return t + dt;
    }// TODO we are just assuming numerical millisecond timestamps right now, but that might not always be the case...

    /** @returns An array of available databases as specified in CouchDB API. */
    async function getDatabases() {
        let response = await fetch(
            `https://${state.host}:${state.port}/_all_dbs`, 
            {}//mode:'cors'}
            );
        if (!response.ok)
            throw( response );
        let json = await response.json();
        return (json);
    }

    async function getStatus() {
        let response = await fetch(`http://${state.host}:${state.port}/`, {mode:'cors'});
        if (!response.ok)
            throw( response );
        let json = await response.json();
        return (json);
    }

    async function getSession(name, password) {
        let content = JSON.stringify( {name, password} );
        return await fetch( '/_session', {
            method: 'POST',
            body: content,
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': content.length
            } }
        );
    }

    
}

// DEBUG
//let startkey='[1562115225603,"s2"]', endkey = '[1562115229903, "r2"]';
// let startkey='[1562115225603,""]', endkey = '[1562115229903, ""]';
// let cdbclient = Couch({host:"localhost", port:5984, db:'simulation'});
// cdbclient.open()
//     .then( async(open)=>{
//         console.log(open)
//         let data = null;
//         do {
//             data = await cdbclient.step(1000);
//             console.log(data);
//         } while(data.offset < data.total_rows);
//     }).catch( (e)=>console.log(e) );
    
    // Couch's get databaseses method again, but in the chaining idiom...
    // function getDatabases() {
    //     fetch( `http://${hostname}:${port}/_all_dbs`, {mode:'cors'})
    //         //{method:'GET',mode:'cors', credentials:'include'} )
    //     .then( (response)=>{
    //         console.log(response);
    //         for (let header of response.headers)
    //             console.log(header);
    //         return response.json()
    //     })
    //     .then(json=>console.log(json))
    //     .catch( (error)=>console.log(error) );
    // }