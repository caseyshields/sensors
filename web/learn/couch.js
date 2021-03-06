
main();
async function main() {
     let xhrsession = await getSession( 'name', 'password' );
     console.log(xhrsession);

     let fetchsession = await fetchSession( 'name', 'password' );
     console.log(fetchsession);
}

async function getSession(name, password) {
    return new Promise( (resolve, reject) => {
        let content = `name=${name}&password=${password}`;
        let request = new XMLHttpRequest();
        request.addEventListener("load", load);
        request.addEventListener('error', reject);
        request.addEventListener('timeout', reject);
        request.addEventListener('abort', reject);
        request.open("POST", "https://localhost:6984/_session");
        request.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');//json');
        // request.setRequestHeader('Access-Control-Allow-Methods', '*');
        // request.setRequestHeader('Access-Control-Allow-Methods', 'POST');
        // request.setRequestHeader('Access-Control-Allow-Headers', 'Set-Cookie');
        request.send(content);
        function load() {
            resolve( {
                headers: request.getAllResponseHeaders(),
                body: request.response
            } );
        }
    });
}

// Access-Control-Allow-Origin: https://foo.example
// Access-Control-Allow-Methods: POST, GET, OPTIONS
// Access-Control-Allow-Headers: X-PINGOTHER, Content-Type
async function fetchSession(name, password) {
    return new Promise( (resolve, reject) => {
        let credentials = JSON.stringify( {name, password} );
        let options = {
            method: 'POST',
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
        fetch( 'https://localhost:6984/_session', options ).then(response => {
            if (!response.ok)
                reject(response.statusText);

            // huh... the cookie is not in the headers like it says in the docs. Great.
            for (let entry of response.headers.entries()) {
                console.log(entry);
            }
            //oh, it's a CORS restriction. You can only acces 6 standard headers...

            // fetch does this but XHTTPRequest doesn't. everything is dumb.

            let body = response.json();

            resolve( body );
        });
    });
}
