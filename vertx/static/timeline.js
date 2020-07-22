/** The Timeline buffers and dispatches a sliding time window of streaming events.
 * Events are efficiently stored in a circular array buffer, and events are dispatched 
 * when they enter and exit the moving interval of displayed data
 */
export default ( dispatch, duration ) => {

    let _size = 1024; // the current capacity of the buffer
    let _data = new Array( _size ); // the allocated circular buffer

    let _head = 0;// the buffer index of the oldest buffered event
    let _tail = 0;// the buffer index of the newest buffered event

    let _new = 0; // the newest bound index of the displayed interval
    let _old = 0; // the oldest bound index of the displayed interval

    let _time; // current time of the simulation
    
    let _displayDuration = duration; // period of time to dispatch to components

    // client side playback and event distribution
    let _dispatch = dispatch;

    let timeline = {

        /** @returns the number of events currently buffered */
        size : function() { return _tail - _head; },

        isEmpty : () => (_head==_tail),
        
        start : () => _data[ _head ].time,

        time : (t) => (t) ? _time = t : _time,

        end : () => _data[ prev(_tail) ].time,

        duration : () => (_head==_tail) ? 0 : _data[ prev(_tail) ].time - _data[ _head ].time,

        remaining : () => (_head==_tail) ? 0 : _data[prev(_tail)].time - _time,

        expire : () => _time - _displayDuration,

        // /** Register a display window listener, as in d3-dispatch.on() */
        // on : function( typenames, callback ) {
        //     _dispatch.on( typename, callback );
        // },

        /** add the given event to the circular buffer. */
        buffer : function( event ) {

            // make sure event has a time, and it is monotonically increasing
            if (!event.time)
                return false;

            if (_head!=_tail && _data[prev(_tail)].time > event.time)
                return false;
            // TODO is there a better way to handle errors?
            // remember this method will always be in a tight loop...
            
            // update time if needed
            if (!_time)
                _time = event.time;
            // TODO possibly set this when a data source is opened- don't check for it every single buffer...

            push( event );
    
            // TODO we need to add an upper limit on the number of items that can be buffered
            // or failing that, we need to adda public method(s) to control the buffered interval
            // example using buffer interval;
            // removed expired, undisplayed events from the buffer
            // let end = _data[ prev(_tail) ].time;
            // let expired = end - _bufferDuration;
            // while (_head < _old && _data[_head].time < expired)
            //     shift();
            
            return true;
        },
    
        /** advances the display window bounds by the given amount of timeand dispatches the related events. */
        step : function( dt ) {
    
            if (timeline.isEmpty())
                return;
            
            _time = (_time) ? _time + dt : start();
    
            // dispatch the interval of events
            while (_new!=_tail && _data[ _new ].time < _time) {
                _dispatch.call( 'occur', this, _data[ _new ] );
                _new = next( _new );
            }
    
            // expire events now outside the displayed interval
            let expired = _time - _displayDuration;
            while ( _old!=_new && _data[ _old ].time < expired) {
                _dispatch.call( 'expire', this, _data[ _old ]);
                _old = next(_old);
            }
        },

        /** Expires everything in the buffer then seeks the beginning of the empty buffer to the specified time. */
        seek : function( time ) {
            
            // expire everything in the display window
            while (_old!=_new) {
                _dispatch.call('expire', this, _data[_old]);
                _old = next(_old);
            }

            // TODO use interploation followed by linear search to find the time...

            // find the start of the display window in the buffer
            _time = time;
            let expired = time - _displayDuration;
            _old = _head;
            while (_data[_old].time < expired)
                _old = next(_old);
            
            // // find the end of the display window in the buffer
            _new = _old;
            // while (_data[_new].time < time) {
            //     _dispatch.call('occur', this, _data[new]);
            //     _new = next(_new);
            // } // todo make seek produce an empty interval and step?
        }, // TODO we should have some returned interval so the user knows the bounds...
        
        // TODO implements a seek which first checks if it can use any of the currently buffered data...
        // also use an interpolation seach for the first guess, assume time distribution is uniform
        // seek : function( t ) {
        //     let begin = _data[ _head ].time;
        //     let end = _data[ prev(_tail) ].time;
        //     if (begin<t && t<end) {
                // while (let n=_head; n<_tail; n++)
                //     if (_data[n]>=time) break;
                //     else n++;
                // TODO figure out end on display bounds
        //     } else {

        //     }
        //     _time 
        // } // TODO should return the bufered interval so the user can decide where to buffer from...

        /** Clears all data buffered in the timeline. */
        clear : function() {
            _head = 0;
            _tail = 0;
            _new = 0;
            _old = 0;
            _time = 0;
        }
    };

    function next(index) { return (index+1)%_size; }
    
    function prev(index) { return (index-1+_size)%_size; } // avoid using comparison...

    function get(index) {
        if (index >= timeline.size())
            return undefined;
        return _data[ (_head+index)%_size ];
    }

    function push( item ) {
        if (next(_tail) == _head)
            reallocate(); // maybe fire a callback for overflow here?
        // TODO possible memory allocation error?

        _data[ _tail ] = item;
        _tail = next(_tail)
    }

    function shift() {
        // check if queue is empty
        if (_head == _tail)
            return undefined;
        
        let item = _data[_head];
        _head = next(_head);

        return item;
    }
    
    function reallocate() {
        let size = _size * 2;
        let buffer = new Array( size );
        
        // copy old data to front of new buffer
        let i = 0;
        for (let n=_head; n!=_tail; n=next(n)) {
            buffer[i++] = _data[n];
        }

        // adjust display interval pointers
        _new = (_head<=_new) ? _new-_head : _new+(_size-_head);
        _old = (_head<=_old) ? _old-_head : _old+(_size-_head);

        //swap buffers, reset pointers
        _head = 0;
        _tail = i;
        _data = buffer;
        _size = size;
    }

    return timeline;
}

// TODO add controls for changing the display and buffer interval
// TODO should we add a subtype to the dispatched event? ie. 'occur.(source)' or 'expire.(type)'
