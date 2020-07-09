const d3_hierarchy = require( 'd3-hierarchy');
const Simulator = require( '../simulator/simulator.js' );

const host = process.argv[2]; // CouchDb host name
const port = 5984; // default api port for CouchDB
const cdb = require('./db.js')(host, port);

// get command line args
const db = 'sensors';
const auth = process.argv[3];
console.log( `{db:'${db}', auth:'${auth}'}` );
// TODO should probably get these from a bound volume or something...

// start the application as soon as we can connect to the couchdb and get instance metadata
async function testConnection() {
    try {
        let info = await cdb.info();
        if (info.couchdb!='Welcome')
            throw info;
        else {
            console.log(info);
            main(info);
        }
    } catch (error) {
        console.log(error);
        setTimeout(testConnection, 5000);
    }
}
testConnection();

async function main(info) {
    try {
        // get the available databases
        let dbs = await cdb.allDbs(auth);
        console.log( `Available Couch databases;\n${JSON.stringify(dbs,null,' ')}` );

        // if CouchDB doesn't contain the given database, we create it
        if (!dbs.includes(db)) {
            console.log( `No Database named ${db}, creating;` );
            let created = await cdb.putDb(auth, db);
            console.log( JSON.stringify(created,null,' ') );
        }

        // check if the time index exists
        let name = 'time_index';
        console.log( `Available indices for ${db};` );
        let indices = await cdb.getIndex(auth, db, name);
        console.log( JSON.stringify(indices,null,' ') );

        // create it if it doesn't
        if (!indices.indexes.find((i)=>i.name==name)) {
            console.log( `No index named '${name}', creating;` );
            let index = await cdb.postIndex( auth, db, {
                index:{
                    fields: ['stamp']
                },
                name,
                type:'json'
            } );
            console.log( JSON.stringify(index, null, ' ') );
        }// index syntax at http://127.0.0.1:5984/_utils/docs/api/database/find.html#db-index
        
        // find out if the database already has any documents in it
        let response = await cdb.findDocs(auth, db, {
            selector:{
                time: {$gt:0}
            },
            limit:1
        }); // query syntax at http://127.0.0.1:5984/_utils/docs/api/database/find.html#db-find

        // run the simulator and upload the data if it doesn't
        if (!response.docs.length) {
            console.log( `Database empty, uploading simulation data;`)
            let config = getSimulationConfig();
            await uploadSimulation(db, config);
            console.log(
`The database at http://${host}:${port}/${db} is initialized using the following configuration;
${JSON.stringify(config,null,' ')}\n`
            );
            //TODO pushing individual documents is very slow, might want to consider bulk operations if that ever becomes an issue...
            // https://docs.couchdb.org/en/stable/api/database/bulk-api.html#inserting-documents-in-bulk
        }

        // TODO test a paging strategy by reading the database...

    } catch (error) {
        console.log(error);
    }
};

function getSimulationConfig() {
    // TODO might want to load this from a file instead
    return config = {
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
}

async function uploadSimulation( db, config ) {

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
            console.log( event );
            let result = await cdb.postDoc(auth, db, event);
            //console.log( JSON.stringify(result, null, ' ') );
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