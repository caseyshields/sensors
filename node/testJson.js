const fs = require( 'fs' );
const d3_hierarchy = require( 'd3-hierarchy' );
const Simulator = require( './simulator/simulator.js' );

/** Runs a simulation and writes the events to a JSON file. */

// load the configuration
let config = {
    start : Date.now(),
    end : Date.now() + 60000,
    dt : 100,
    path : './node/json/data.json',
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
let data = [];
let simulation = Simulator( network, config );
for (let time=config.start; time<config.end; time+=config.dt) {
    let frame = simulation.update(time);

    // buffer the event and write it to the console
    for (let event of frame) {
        data.push( event );
        console.log( event );
    }
}

// write the dataset to a JSON file
try {
    fs.writeFileSync( config.path, JSON.stringify( data, null, 2 ) );
} catch( error ) {
    console.log( error );
}
