
// TODO authenticate the client first...

// get the list of missions from the server
fetch( 'http://localhost:43210/api/missions',
    {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
    } )
.then( response => {
    if (!response.ok)
        reject(response.statusText);
    response.json().then( json => {
        // create clickable titles for each mission
        let titles = d3.select("#missions")
            .selectAll('h3')
            .data( json )
            .enter()
            .append('h3')
            .on('click', d=>console.log )
            .attr('id', d=>d )
            .html( d=>d );
    });
})
.catch( console.error );

// TODO add actions to each mission to get the available products

// TODO add actions to each mission to start streaming view data and adding it to the DOM