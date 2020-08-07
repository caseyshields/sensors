
// TODO authenticate

// TODO get databases from vertx

let databases = [
    'YYYYJJJ-PPPP-001',
    'YYYYJJJ-PPPP-002',
    'YYYYJJJ-PPPP-003'
];

let credentials = JSON.stringify( {name, password} );
let options = {
    method: 'GET',
    mode: 'cors',
    headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'Content-Length': credentials.length
        // 'Access-Control-Allow-Origin': '*',
        // 'Access-Control-Allow-Methods': 'POST',
        // 'Access-Control-Allow-Headers':'Set-Cookie' // does this help?
    },
    body: credentials
};

fetch( 'http://localhost:43210/api/missions', options ).then(response => {
    if (!response.ok)
        reject(response.statusText);

    let body = response.json();

    // create clickable titles for each mission
    let titles = d3.select("#missions")
        .selectAll('h3')
        .data( databases )
        .enter()
        .append('h3')
        .on('click', d=>console.log )
        .attr('id', d=>d )
        .html( d=>d );

    resolve( body );
});

//// factory for a mission button component
//function createMissions() {
//
//    let clicker = d=>{};
//
//    function mission(parent, data) {
//        let titles = parent.selectAll('h3')
//            .data(data);
//
//        titles.exit().remove();
//
//        titles = titles.enter().append('h3')
//                .on('click', clicker)
//                .attr('id', d=>d)
//                .html( d=>d )
//            .merge(titles);
//    }
//
//    mission.clicker = function( c ) {
//        if (c==undefined || !c)
//            return clicker;
//        clicker = c;
//        return mission;
//    }
//
//    return mission;
//}