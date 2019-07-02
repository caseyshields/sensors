
// Node export
module.exports = create;

/** Create a sensor which scans for targets, and notifies it's command center
 * @param {Object} node - A d3 Hierachy leaf node corresponding to the sensor to be simulated
 * @param {Object} node.data.lon - the longitude of the sensor
 * @param {number} node.data.lat - the latitude of the sensor
 * @param {number} node.data.spin - the rate of spin for the sensor
*/
function create( node ) {
    
    let a0 = heading(0);
    let a1 = a0;
    let rMax = 3;
    let rBias = 1/60;
    // TODO move this stuff into config?

    let sensor = {
        
        node: ()=>node,
        
        /** sweep the sensor beam, noting any targets the beam crosses.  and sending detection messages up the network */
        update: function( time, simulator ) {
            // update the angular sweep of the sensor
            a0 = a1;
            a1 = heading(time);

            // search for targets within the sweep of the sensor
            for (target of simulator.targets) {
                
                // compute polar coordinates of target relative to sensor
                let dx = + target.longitude() - node.data.lon;
                let dy = + target.latitude() - node.data.lat;
                let r = Math.sqrt( dx*dx + dy*dy );
                let a = Math.atan2( dy, dx );

                // if target is within sweep, remembering to check for angle rollover!
                if ( (a0<a1 && (a0<=a && a<a1)) || (a0>a1 && (a0<a || a<a1)) ) {
                    // I should really instead use a dot product test; (a*a1+a*a0) - (a0*a1) < epsilon

                    //create a plot message from a noisy measurement, and send it
                    let measurement = sensor.measure( a, r );
                    simulator.route( time, node, measurement );
                }
            }
        },

        /** Add an error model to the given polar coordinates, create a message and route it up the network. */
        measure: function( azimuth, range ) {

            // if coordinates weren't supplied generate random ones in the slice
            // if (!azimuth || !range) {
            //     if( a0<a1 )
            //         azimuth = (Math.random*(a1-a0) + a0);
            //     else
            //         azimuth = (Math.random*(a0+a1) + a0) % (Math.PI*2);
            //     range = Math.random() * rMax;
            // }

            // add error
            range += rBias;
            //angle += (2*aErr*Math.random() - aErr);
            // TODO make this a configurable function

            // figure out resulting geodetic coordinates using near-equator flat approximation
            let x = node.data.lon + range * Math.cos(azimuth);
            let y = node.data.lat + range * Math.sin(azimuth);
            // TODO instead use a navigation method for better accuraccy

            return {
                range,
                angle: 90 + 360 * (azimuth) / (Math.PI*2),
                power: 1,
                glyph: 'circle',
                lon: x,
                lat: y,
                class: 'plot'
            }
        },

        /** Sends a received beacon message up the sensor network */
        strobe: function( time, simulator, azimuth ) {
            simulator.route( time, node, {
                    angle: 90 + 360 * (azimuth) / (Math.PI*2),
                    class: 'strobe'
                    // strobe: true,
            }   );
        }
    }

    function heading(time) {
        let v = (node.data.spin*1000);
        let a = ((time / v) % 2.0) - 1.0;
        return a * 2*Math.PI;
    }

    return sensor;
}