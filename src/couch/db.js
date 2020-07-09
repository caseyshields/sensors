/** Wraps Node HTTP requests to the CouchDB Rest API in Promises.
 * Only contains a subset used for learning, and pushing simulator data. */
const http = require( 'http' );

module.exports = function( host, port ) {

return {info, allDbs, putDb, getIndex, postIndex, findDocs, postDoc}

async function info() {
    return await request({
        host, port,
        method: 'GET',
        path: '/'
    });
}

async function allDbs(auth) {
    return await request({
        host, port, auth,
        method: 'GET',
        path: '/_all_dbs'
    });
}

async function putDb( auth, db ) {
    return await request({
        host, port, auth,
        method: 'PUT',
        path: `/${db}`
    });
}

async function getIndex(auth, db, index) {
    return await request({
        host, port, auth,
        method: 'GET',
        path: `/${db}/_index`
    })
}

async function postIndex( auth, db, index ) {
    const content = JSON.stringify(index);
    return await request({
        host, port, auth,
        method: 'POST',
        path: `/${db}/_index`,
        headers: {
            'Content-Type' : 'application/json',
            'Content-Length' : content.length
        }
    }, content);
}

async function findDocs(auth, db, query) {
    const content = JSON.stringify( query );
    return await request({
        host, port, auth,
        path: `/${db}/_find`,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': content.length
        }
    }, content);
}

async function postDoc( auth, db, doc ) {
    const content = JSON.stringify( doc );
    return await request({
        host, port, auth,
        path: '/'+db,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': content.length
        }
    }, content);
}

/** Asynchronously requests json from an API */
function request(options, content) {
    return new Promise( (resolve, reject) => {
        buffer = '';
        const request = http.request(options, response => {
            response.on('data', (d)=>{buffer+=d} );
            response.on('end', ()=>{
                let json = JSON.parse(buffer);
                if (response.statusCode>400)
                    reject(json);
                resolve(json); 
            });
            response.on('error', (e)=>reject(e))
        });
        request.on('error', (e)=>reject(e));
        if(content)
            request.write( content );
        request.end();
    });
} //TODO I'm just messing with the CouchDB REST API, I should probably use something like nano or pouch...
// TODO I can't figure out how to make this work with async/await...
// async function xrequest(options) {
//     buffer = '';
//     const request = http.request(options, response => {
//         console.log(`statusCode: ${response.statusCode}`)
//         response.on('data', (d)=>{buffer+=d} );
//         response.on('end', ()=>{return JSON.parse(buffer);} )
//         response.on('error', (error)=>{console.log(error); throw error;})
//     });
//     request.on('error', error=> {console.log(error); throw error;});
//     request.end();
// }

}