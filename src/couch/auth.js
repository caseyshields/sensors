let arg = {
    host : process.argv[0],
    port: process.argv[1],
    user: process.argv[2],
    pass: process.argv[3],
    db: process.argv[4]
}
let app = {
    host: '127.0.0.1',
    port: 5984,
    user: 'admin',
    pass: 'password',
    db: 'sensors'
}
// Object.assign( app, arg );

const cdb = require('./db.js')(app.host, app.port);

main();
async function main() {

    let status = await cdb.info();
    console.log( status );

    try {
        let session = await cdb.session(app.user, app.pass);
        console.log( session );
    } catch (exception) {
        console.log(exception);
    }
}