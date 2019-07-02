const http = require( 'http' );

const options = {
    hostname: '127.0.0.1',
    port: 5984,
    path: '',
    mathod: 'GET'
}

const request = http.request(options, response => {
    console.log(`statusCode: ${response.statusCode}`)

    response.on('data', d=> {
        process.stdout.write(d)
    });

});

request.on('error', error => {
    console.error(error);
})

request.end()