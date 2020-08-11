
// TODO authenticate the client first...

// get the list of missions from the server
fetch( 'http://localhost:43210/api/missions',
    {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
        }
    } )
.then( response => {
    if (!response.ok)
        console.log(response.statusText);
    response.json().then( json => {
        // create clickable titles for each mission
        let titles = d3.select("#missions")
            .selectAll('h2')
            .data( json )
            .enter()
            .append('div')
            .on('click', addProducts )
            .append('h2')
            .attr('id', d=>d )
            .html( d=>d );
    });
})
.catch( console.error );


function addProducts( umi, index, tags ) {

    let mission = d3.select( tags[index] );
    //console.log( [this, umi, tag] );

    fetch('http://localhost:43210/api/mission/'+umi, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    } )
    .then( response => {
        if (!response.ok)
            console.log(response.statusText);
        response.json().then( json => {
            // create clickable titles for each product
            let products = mission.selectAll('h3')
                .data( json )
                .enter()
                .append('div')
                .attr('data-umi', umi)
                .on('click', addEvents )
                .append('h3')
                .html( d=>d );
        });
    });
}

function addEvents(product, index, tags) {
    let mission = d3.select( tags[index] );
    let umi = mission.attr('data-umi');
    console.log(umi);
    fetch('http://localhost:43210/api/mission/'+umi+'/product/'+product, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    } )
    .then( response => {
        if (!response.ok)
            console.log(response.statusText);
        response.json().then( json => {

            if (json.rows==undefined)
                console.log( JSON.stringify(json) );

            // add each event to the product
            let products = mission.selectAll('h3')
                .data( json.rows )
                .enter()
                .append('h4')
                .html( d=>JSON.stringify(d) );
        });
    });
}
