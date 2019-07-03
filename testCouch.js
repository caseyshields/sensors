const d3_hierarchy = require( 'd3-hierarchy');
const http = require( 'http' );
const Simulator = require( './simulator/simulator.js' );

const hostname = '127.0.0.1';
const port = 5984;
const auth = process.argv[0];
const db = 'simulation';

//TODO I'm just messing with the CouchDB REST API, I should probably use something like nano or pouch...

try {
    // helloCouch( );
    uploadSimulation();
} catch (error) {
    console.log(error);
}

function helloCouch( callback ) {
    let buffer = '';

    // CouchDB HTTP request for available databases
    const options = {
        hostname,
        port,
        path: '/_all_dbs',
        method: 'GET'
    };
    const request = http.request(options, response => {
        console.log(`statusCode: ${response.statusCode}`)
        
        // accumulate couch response
        response.on('data', (d)=>{buffer += d;} );

        // response complete
        response.on('end', ()=>{
            console.log('response ended')
            process.stdout.write(buffer);

            // if CouchDB doesn't contain the given database, we create it
            let databases = JSON.parse(buffer);
            if (!databases.includes(db))
                createDatabase(db);

            //callback();
        });
    });
    
    request.on('error', error => {
        console.error(error);
    });
    
    request.end( ()=>{
        console.log('request ended');
        //callback();
    });
}

function createDatabase( name ) {

    const options = {
        hostname,
        port,
        auth,
        path: `/${name}`,
        method: 'PUT'
    };
    const request = http.request(options, response => {
        console.log(`statusCode: ${response.statusCode}`)
        response.on('data', d=>{
            process.stdout.write(d);
        });
    });

    request.on('error', error=> {
        console.log(error);
    });

    request.end();
}

function uploadSimulation() {
    // load the configuration
    let config = {
        start : Date.now(),
        end : Date.now() + 60000,
        dt : 100,
        path : './data/test.json',
        laydown: [
            { class:"hq", parent: "", type:"headquarters", status:1, sic:11 },
            
            { class:"d1", parent : "hq", type:"command", status:1, latency:0.005, sic:9 },
            { class:"d2", parent : "hq", type:"command", status:1, latency:0.005, sic:10 },

            { class:"r1", parent : "d1", type:"router", status:1, latency:0.005, sic:6 },
            { class:"r2", parent : "d2", type:"router", status:1, latency:0.005, sic:7 },
            { class:"r3", parent : "r2", type:"router", status:1, latency:0.005, sic:8 },
            
            { class:"s1", parent : "r1", type:"sensor", glyph:"glyph1", sic:1, status: 1,
                    lat:35.942, lon:-114.882, spin:10.0, latency:0.005 },
            { class:"s2", parent : "r1", type:"sensor", glyph:"glyph2", sic:2, status: 1,
                    lat:36.242, lon:-115.678, spin:10.0, latency:0.005 },
            { class:"s3", parent : "r2", type:"sensor", glyph:"glyph3", sic:3, status: 1,
                    lat:35.942, lon:-115.493, spin:10.0, latency:0.005 },
            { class:"s4", parent : "r3", type:"sensor", glyph:"glyph4", sic:4, status: 1,
                    lat:36.291, lon:-114.704, spin:10.0, latency:0.005 },
            { class:"s5", parent : "r3", type:"sensor", glyph:"glyph5", sic:5, status: 1,
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