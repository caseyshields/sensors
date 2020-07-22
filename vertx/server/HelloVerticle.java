package server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;


    /** Take from example at https://vertx.io/docs/vertx-docker/
     * */
    public class HelloVerticle extends AbstractVerticle {

        @Override
        public void start() throws Exception {
            vertx.createHttpServer().requestHandler(request -> {
                request.response().end("Hello Java world");
            }).listen(8080);
        }

        public static void main(String[] args) {
            Vertx vertx = Vertx.vertx();
            vertx.deployVerticle(new server.HelloVerticle());
        }
    }

/*
> docker build -t sample/vertx-java .
....
> docker run -t -i -p 8080:8080 sample/vertx-java
* */