#trying to figure out different ways of configuring couchdb...

FROM couchdb
COPY couchdb/config/local.ini /opt/couchdb/etc

# docker build -t my-couchdb .
# docker run -dp 5984:5984 my-couchdb

# docker run -dp 5984:5984 `
# -v ${PWD}/couchdb/data:/opt/couchdb/data `
# -v ${PWD}/couchdb/config:/opt/couchdb/etc/local.d `
# -e COUCHDB_USER=admin `
# -e COUCHDB_PASSWORD=password `
# -d couchdb:latest