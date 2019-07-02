
// Node export
module.exports = create;

/** Create a router which relays messages up the chain to the headquarters.
 * @param {Object} node - A d3 Hierachy node corresponding to the router to be simulated
 * @param {Object} node.parent - the parent link in the sensor hierarchy
 * @param {number} node.data.latency - the delay before messages are routed to thier superiors
 * @param {string} node.data.type - A string representing the type of node, can be one of {router | command | sensor}
*/
function create( node ) {
    let queue = [];
    // TODO add disabled flag that another agent can set

    let router = {
        
        node: ()=>node,
        
        /** Add the given message to the parent's queue. */
        send: function(message) {
            // ignore messages if this node is at the top of the chain
            if (node.parent)
                queue.push( message );
        },

        /** repeat any messages from children after a network delay */
        update: function( time, simulator ) {
            
            // dequeue messages after waiting to simulate network latency
            while (queue.length && time - queue[0].time >= node.data.latency) {
                let received = queue.shift();

                if (node.data.type.startsWith('command'))
                    received.class = 'track';

                // update the address and relay the message
                simulator.route(time, node, received );
            }
        }
    }

    return router;
}
