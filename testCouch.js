const d3_hierarchy = require( 'd3-hierarchy');
const http = require( 'http' );
const Simulator = require( './simulator/simulator.js' );

const hostname = '127.0.0.1';
const port = 5984;
const db = process.argv[2]
const auth = process.argv[3];

//TODO I'm just messing with the CouchDB REST API, I should probably use something like nano or pouch...
(async function main() {
    try {
        // get the available databases
        console.log('Available Couch databases');
        let dbs = await available();
        console.log(dbs);

        // if CouchDB doesn't contain the given database, we create it
        if (!dbs.includes(db)) {
            console.log(`Creating ${db}`);
            console.log( await createDatabase(auth, db) );
        }

        // check if the time index exists
        let name = 'time_index';
        let indices = await getIndex(db, name);
        console.log( indices );
        if (!indices.indexes.find((i)=>i.name==name)) {
            console.log( `No index named '${name}'` );
            console.log( await createIndex( auth, db, {
                index:{
                    fields: ['stamp']
                },
                name,
                type:'json'
            } ) );
            // For documentation look on the local couch server;
            // see http://127.0.0.1:5984/_utils/docs/api/database/find.html#db-index
        }
        
        // await uploadSimulation( db );

        // bufferPages();
    } catch (error) {
        console.log(error);
    }
})();

async function available() {
    return await request({
        hostname, port,
        method: 'GET',
        path: '/_all_dbs'
    });
}

async function createDatabase( auth, db ) {
    return await request({
        hostname, port, auth,
        method: 'PUT',
        path: `/${db}`
    });
}

async function getIndex(db, index) {
    return await request({
        hostname, port,
        method: 'GET',
        path: `/${db}/_index`
    })
}

async function createIndex( auth, db, index ) {
    const content = JSON.stringify(index);
    return await request({
        hostname, port, auth,
        method: 'POST',
        path: `/${db}/_index`,
        headers: {
            'Content-Type' : 'application/json',
            'Content-Length' : content.length
        }
    }, content);
}

/** Asynchronously requests json from an API */
function request(options, content) {
    return new Promise( (resolve, reject) => {
        buffer = '';
        const request = http.request(options, response => {
            response.on('data', (d)=>{buffer+=d} );
            response.on('end', ()=>{
                let json = JSON.parse(buffer);
                if (response.statusCode>400)
                    reject(json);
                resolve(json); 
            });
            response.on('error', (e)=>reject(e))
        });
        request.on('error', (e)=>reject(e));
        if(content)
            request.write( content );
        request.end();
    });
}
// TODO I can't figure out how to make this work with async/await...
// async function xrequest(options) {
//     buffer = '';
//     const request = http.request(options, response => {
//         console.log(`statusCode: ${response.statusCode}`)
//         response.on('data', (d)=>{buffer+=d} );
//         response.on('end', ()=>{return JSON.parse(buffer);} )
//         response.on('error', (error)=>{console.log(error); throw error;})
//     });
//     request.on('error', error=> {console.log(error); throw error;});
//     request.end();
// }

function uploadSimulation( db ) {
    // load the configuration
    let config = {
        start : Date.now(),
        end : Date.now() + 60000,
        dt : 100,
        path : './data/test.json',
        laydown: [
            { class:"hq", tap:"a", parent: "", type:"headquarters", status:1, sic:11 },
            
            { class:"d1", tap:"b", parent : "hq", type:"command", status:1, latency:0.005, sic:9 },
            { class:"d2", tap:"c", parent : "hq", type:"command", status:1, latency:0.005, sic:10 },

            { class:"r1", tap:"d", parent : "d1", type:"router", status:1, latency:0.005, sic:6 },
            { class:"r2", tap:"e", parent : "d2", type:"router", status:1, latency:0.005, sic:7 },
            { class:"r3", tap:"f", parent : "r2", type:"router", status:1, latency:0.005, sic:8 },
            
            { class:"s1", tap:"g", parent : "r1", type:"sensor", glyph:"glyph1", sic:1, status: 1,
                    lat:35.942, lon:-114.882, spin:10.0, latency:0.005 },
            { class:"s2", tap:"h", parent : "r1", type:"sensor", glyph:"glyph2", sic:2, status: 1,
                    lat:36.242, lon:-115.678, spin:10.0, latency:0.005 },
            { class:"s3", tap:"i", parent : "r2", type:"sensor", glyph:"glyph3", sic:3, status: 1,
                    lat:35.942, lon:-115.493, spin:10.0, latency:0.005 },
            { class:"s4", tap:"j", parent : "r3", type:"sensor", glyph:"glyph4", sic:4, status: 1,
                    lat:36.291, lon:-114.704, spin:10.0, latency:0.005 },
            { class:"s5", tap:"k", parent : "r3", type:"sensor", glyph:"glyph5", sic:5, status: 1,
                    lat:36.651, lon:-115.188, spin:10.0, latency:0.005 }
        ]
    };

    // create a D3 Hierarchy for the network laydown
    let network = d3_hierarchy.stratify()
    .id( function(d) {return d.class;} )
    .parentId( function(d) {return d.parent;} )
    ( config.laydown );

    // incrementally advance time for all simulation objects
    let simulation = Simulator( network, config );
    for (let time=config.start; time<config.end; time+=config.dt) {
        let frame = simulation.update(time);

        // post each event to the couch database...
        for (let event of frame) {
            const data = JSON.stringify( event );
            console.log( data );

            const options = {
                hostname,
                port,
                path: '/'+db,
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': data.length
                }
            };

            let reply = ''
            const request = http.request(options, response=>{
                response.on('data', (d)=>(reply+=d) );
                response.on('end', ()=>console.log(reply) );
            });

            request.on('error', error=>console.error(error) );

            request.write(data);

            request.end();
            // TODO use timestamp and source as an id?
        }
    }
}

// A pagination recipe from https://docs.couchdb.org/en/stable/ddocs/views/pagination.html
// might be a useful way to buffer the event timeline
async function bufferPages() {
    try {
        let data = [];
        let page = await getPage(10);
        let total = 0;
        do {
            let events = JSON.parse(page);
            total = events.total_rows;

            for (event of events.rows)
                data.push( event.doc)
            data.pop();// the last item is just to get the next page boundary...

            let startkey = events.rows[events.rows.length-1].key;
            page = await getPage(10, JSON.stringify(startkey) );

        } while (data.length < total);
        console.log( data );
    } catch(error) {
        console.log(error);
    }
}

function getPage( count, startkey ) {
    return new Promise( (resolve, reject) => {

        // Options for CouchDB REST API
        const ddoc='stream'; // design document
        const view='chronological'; // view
        const options = {
            hostname,
            port,
            path: `/${db}/_design/${ddoc}/_view/${view}?limit=${count+1}&include_docs=true`,
            method: 'GET',

        };

        if (startkey)
            options.path += ('&start_key='+startkey);

        // Use Node HTTP module to make a couch view query
        const request = http.request(options, response => {
            let buffer = '';
            // buffer the http response
            response.on('data', (d)=>{buffer += d;} );

            // invoke success or failure callback depending on the status code
            response.on('end', ()=> {
                if(response.statusCode > 400)
                    reject( buffer )
                resolve( buffer );
            } ); // TODO probably should include the header as well...
        });
        
        // if something went wrong, call the failure call back
        request.on('error', error => 
        reject( error )
        );
        
        request.end();
    });

    
}