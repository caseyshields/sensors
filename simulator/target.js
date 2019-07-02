module.exports = create;

/** Creates a sensor target which travels in a circular orbit and can emit a beacon to the nearest sensor
 * @param {Number} cx - the x coordinate of the center of the circular path
 * @param {Number} cy - the y coordinate of the center of the circular path
 * @param {Number} r - the radius of the circular path
 * @param {number} v - the speed of the target
 * @param {boolean} strobe - whether or not the target emits a signal
 */
function create( cx, cy, r, v, strobe) {

    let lastStrobe = 0;
    let strobeRate = 10000; // milliseconds
    let p = position(0);

    let target = {
        
        latitude : ()=>p[0],

        longitude : ()=>p[1],

        radius : () => r,

        velocity : () => v,

        update: function(time, simulator) {
            // update the target postion
            p = position(time);

            // if this target strobes, and is due
            if (strobe && time-lastStrobe > strobeRate) {

                // find the closest sensor(s), at distance(d)
                let sensor, polar;
                for (s of simulator.sensors) {
                    let p = polarCoordinates(this, s);
                    if (!sensor || polar.r > p.r) {
                        sensor = s;
                        polar = p;
                    }
                }

                // have that sensor generate a strobe alert message
                sensor.strobe(time, simulator, polar.a);
                // TODO consider relative angle...
                // determine intensity?

                // TODO inject false plot into that sensor at some rate...
                // while (time-lastStrobe >= strobeRate) {
                //     sensor.plot(); // TODO send bogus plot to sensor, it needs to infer it's being jammed and relay a warning!
                //     lastStrobe += strobeRate
                // }

                lastStrobe = time;
            }
        }
    }

    function polarCoordinates( target, sensor ) {
        let node = sensor.node(); // get the d3 node for layout
        let data = node.data; // get the sensor configuration

        // let dx = data.lat - target.latitude();
        // let dy = data.lon - target.longitude();
        // let dx = + target.longitude() - data.lon;
        // let dy = + target.latitude() - data.lat;
        
        let dx = + target.longitude() - data.lon;
        let dy = - target.latitude() + data.lat;
                
        let r = Math.sqrt( (dx*dx) + (dy*dy) );
        let a = Math.atan2( dy, dx );
        return {a, r};
    }
    
    function position( time ) {
        let angle = time * v / r;
        let lon = cx + r * Math.cos(angle);
        let lat = cy + r * Math.sin(angle);
        return [lat, lon];
    }

    return target;
}

// TODO allow arbitrary parametrically defined paths, possible by allowing the position function to be mutable...