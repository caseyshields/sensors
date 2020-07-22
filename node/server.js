

/** An express server which streams the simulated data over a WebSocket using a custom protocol. */

const Simulator = require( './simulator.js' );
const express = require('express');
const app = express();
const ws = require('express-ws')(app);
const fs = require('fs');
const d3_hierarchy = require( 'd3-hierarchy' );

let time = 0;
let index = 0;
let data = [];
let config = {};

app.ws('/echo', (sock, req) => {

  console.log("Websocket opened");

  sock.on('message', function(msg) {

    let json = JSON.parse( msg );
    console.log(json);
    console.log();

    switch( json.type ) {

    // user connects to the topic
    case "subscribe":
      time = 0;
      index = 0;
      data = [];

      // send the user the time so they can sync up!
      json.time = time; // TODO should be named start

      // TODO technically this should receive and save the user, topic and configuration
      config = json.config;

      // echo response
      sock.send( JSON.stringify(json) );
      break;

    // open just one simulated dataset
    case "open":
      // load the configuration data
      if (data.length>0) {
        //json.error = {message:"This simulator only supports one simulated dataset, and it has already been opened.", code:7};
        // console.log( 'Already open...')

      } else {
        // create a tree for the laydown
        let network = d3_hierarchy.stratify()
            .id( function(d) {return d.class;} )
            .parentId( function(d) {return d.parent;} )
            ( config.laydown );

        // run and buffer the simulation
        //let params = config.data.simulate;
        //data = sim.simulateNetworkTree( network, params);
        // // ah, flatten the source and target values- TODO probably need to move this to the simulator
        // for (event of data) {
        //   event.source = event.source.id;
        //   event.target = event.target.id;
        // }
        // // console.log( data );

        // new simulator!
        let args = config.data[1];// TODO get these from a more reasonable place!
        args.start = Date.now();// d3.now();
        let simulator = Simulator( network, args );
        for (let n=0; n<20000; n+=100) {
          let events = simulator.update(n);
          for (let event of events) {
            data.push( event );
            console.log(event);
          }
        }

        // for this hack with one possible source, just set the time to the start of the data
        time = data[0].time;

        // add a summary of the new data
        json.size = data.size;
        json.start = time; //TODO should I add this to the reply?
      }

      // send it back to the user
      sock.send( JSON.stringify(json) );

      break;

    case "step":
      if (!json.interval)
        json.error = { message:"missing 'interval' parameter", code:"1" };
      else if (!data)
        json.error = { message:"No open data", code:"2" };
      else {
        // advance the read head of the dataset
        time += json.interval;
        json.time = time;

        // buffer the intermediate events
        json.data = [];
        while (index<data.length && data[index].time < time) {
          let event = data[index];
          // console.log( JSON.stringify( event ) );
          json.data.push( data[index] );
          index++;
        }

        // if (index>=data.length)
        //   json.data = undefined;
      }

      // send it back to the user
      sock.send( JSON.stringify( json ) );
      break;

    case "seek":
      if (json.time == null || json.time == undefined)
        json.error = { message:"missing 'interval' parameter", code:"3" };
      else if (!data)
        json.error = { message:"No open data", code:"4" };
      else {
        // scan the the specified time
        time = json.time;
        index = 0;
        while (data[index] < time) {
          json.interval.push( data[index] );
          index++;
        }
      }

      // send new time back to user
      sock.send( JSON.stringify( json ) );
      break;

    case "say":
      // really this needs to be reiterated to all connected users...
      sock.send( JSON.stringify( json ) );
      break;

    case "close":
      // for this dummy example I just close the simulated data
      time = 0;
      index = 0;
      data = 0;
      sock.send( JSON.stringify( json ) );
      break;

    default:
      msg.error = { message:"Unrecognized command", code:"5" };
      sock.send( msg );
    }
  });

  sock.on('close', function(){
    console.log("Websocket closed");
  });

  // console.log( 'socket', req );
});

//// host static files
//let options = {
//  etag : false,
//  extensions : ['htm', 'html'],
//  fallthrough : false,
//  index : 'index.html',
//  redirect: false
//}
//app.use( express.static('./', options) );
//console.log( 'serving static files' );

let port = 43210;
app.listen( port );
console.log( 'listening on ', port );

//// https express example /////////////////////////////////////////////////////////////////
// const https     = require('https')
// const fs        = require('fs')
// const express   = require('express')
// const expressWs = require('express-ws')

// const serverOptions = {
//   key: fs.readFileSync('key.pem'),
//   cert: fs.readFileSync('cert.pem')
// }

// const app       = express()
// const server    = https.createServer(serverOptions, app)

// expressWs(app, server)

// app.ws('/echo', (ws, req) => {
//     ws.on('message', msg => {
//         ws.send(msg)
//     })

//     ws.on('close', () => {
//         console.log('WebSocket was closed')
//     })
// })

// server.listen(443)