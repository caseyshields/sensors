
// TODO authenticate

// TODO get databases from vertx

let databases = [
    'YYYYJJJ-PPPP-001',
    'YYYYJJJ-PPPP-002',
    'YYYYJJJ-PPPP-003'
];



// factory for a mission button component
function createMissions() {

    let clicker = d=>{};

    function mission(parent, data) {
        let titles = parent.selectAll('h3')
            .data(data);

        titles.exit().remove();

        titles = titles.enter().append('h3')
                .on('click', clicker)
                .attr('id', d=>d)
                .html( d=>d )
            .merge(titles);
    }

    mission.clicker = function( c ) {
        if (c==undefined || !c)
            return clicker;
        clicker = c;
        return mission;
    }

    return mission;
}


let selection = d3.select('#mission');
let mission = createMissions()
    .clicker( d=>console.log );
mission( d3.select("#missions"), databases );