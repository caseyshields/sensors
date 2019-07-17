// import the simulation actors
const Router = require('./router.js');
const Target = require('./target.js');
const Sensor = require('./sensor.js');

// Node export
module.exports = create;

// TODO rather than assuming the network is a tree, we need to be able to handle graph topologies...

/** Creates a simulator for a sensor network
 * @param {Object} network - a D3 hierarchy which describes the network which the sensors are connected to
 * @param {Object} config - a configuration object containing simulator parameters
*/
function create( network, config ) {
    
    let args = {
        type : 'simulate', 
        title : 'Simulate Test Data',
        velocity : 0.000001,
        radius : 1,
        longitude : -115.805,
        latitude : 35.887,
        rangeError : 0.02,
        duration : 980000,
        start : 0,
        scenario : 'circles'
    }
    Object.assign( args, config );

    let index = {}; // an index of all nodes in the network
    let routers = []; // the set or routers in the network
    let sensors = []; // the set of sensor in the network
    let events = []; // Events generated by the simulation are buffered here

    // construct the target simulators
    let targets = []
    if (args.scenario==='circles') {
        targets.push( Target( args.longitude, args.latitude, args.radius, args.velocity, true ) );
        for (let n=0; n<4; n++) {
            let t = targets[n];
            let target = Target(
                t.longitude() + t.radius() * Math.cos(n*Math.PI/2) / 2,
                t.latitude() + t.radius() * Math.sin(n*Math.PI/2) / 2,
                t.radius() / 2,
                t.velocity(),
                false );
            targets.push( target );
        }
    // The default scenario is a stationary grid over vegas...
    } else {
        for (let lat=35.0; lat<37.0; lat+=0.2)
            for (let lon=-117.0; lon<-114.0; lon+=0.2)
                targets.push( {
                    latitude: ()=>lat,
                    longitude: ()=>lon,
                    update: (time, simulator)=>{}
                });
    }

    // instantiate the individual component simulating the network
    network.each( function(node) {
        let simulcra;
        switch(node.data.type) {
            case 'sensor' :
                simulcra = Sensor( node );
                sensors.push( simulcra );
                break
            
            case 'command' : // TODO make a simulator for trackers
            case 'headquarters' : // TODO make a simulator for trackers
            case 'router' : 
                simulcra = Router( node );
                routers.push( simulcra );
                break;
        }
        index[node.id] = simulcra;
    } );
    
    return {
        start : args.start,
        targets,
        index,
        sensors,
        routers, // TODO probably shouldn't directly expose these

        /** advance all of the simulation components to the given time, collecting the events they generate */
        update: function(time) {
            events = [];

            // first update the targets
            for (let target of targets)
                target.update(time, this);

            // TODO add hacker simulcra who target routers!
    
            // then update all the nodes in reverse order
            network.eachAfter( (node)=>{
                let simulcra = index[node.id];
                simulcra.update(time, this);
            });

            return events;
        },

        /** Addresses to the given message, buffers it in the event stream, and routes it up the network tree.
         * @param [Object] source - The d3 hierarchy node which emits the message
        */
        route: function( time, source, body ) {
            // create a time stamp
            let t = new Date();
            t.setTime( time );

            // create appropriate metadata for the given plot or route event's parameters
            let addressing = {
                time : t.getTime(),
                stamp : t.toISOString(),
                target : source.parent.id,
                source : source.id,
                sic : source.data.sic,
                tap : source.data.tap,
                // class : source.data.class // originator should supply class
            };
        
            // merge addressing and measurement data
            let message = {};
            Object.assign( message, body );
            Object.assign( message, addressing );
        
            // we assume every message is captured as an event
            events.push( message );

            //queue the plot message with the parent node
            let parent = index[source.parent.id];
            parent.send( message );

            return message;
        }

    }
}
