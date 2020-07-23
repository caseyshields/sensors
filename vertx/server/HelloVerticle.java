package server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;


public class HelloVerticle extends AbstractVerticle {

    private HttpServer server;

    public void start(){//Promise<Void> startPromise) {
//        context();
//        httpServer(startPromise);
//        timers();

        // set up some event bus handlers
        EventBus bus = vertx.eventBus();
        MessageConsumer<String> consumer = bus.consumer("example.test");
        consumer.handler( message->{
            System.out.println( message.address() );
            System.out.println( message.replyAddress() );
            message.headers().forEach( header->{
                System.out.println( header.getKey()+'='+header.getValue() );
            });
            System.out.println(message.body().toString());
        });
        consumer.completionHandler( result->{
            if (result.succeeded()) {
                System.out.println("all nodes registered");
//                startPromise.complete();
            } else {
                System.out.println("Registration failed");
//                startPromise.fail("Registration failed");
            }
        });

        bus.publish("example.test", "example message");
    }

    public void context() {
        // verticles can be passed json configuration objects when they are started
        JsonObject config = config();
        System.out.println( config.toString() );

        // might need to figure out the specific type of verticle?
        Context context = vertx.getOrCreateContext();
        if (context.isEventLoopContext()) {
            System.out.println("Context attached to Event Loop");
        } else if (context.isWorkerContext()) {
            System.out.println("Context attached to Worker Thread");
        } else if (context.isMultiThreadedWorkerContext()) {
            System.out.println("Context attached to Worker Thread - multi threaded worker");
        } else if (! Context.isOnVertxThread()) {
            System.out.println("Context not attached to a thread managed by vert.x");
        }
    }

    public void httpServer(Promise<Void> startPromise) {
        // this is blocking, which is why we overrode the async start method...
        server = vertx.createHttpServer().requestHandler(req -> {
            req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x!");
        });
        server.listen(8080, res->{
            if (res.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail( res.cause() );
            }
        });
    }

    public void timers() {
        // playing with timers in the event loop...
        long timer1 = vertx.setTimer(1000, id->{
            System.out.println("timer "+id);
        });
        long timer2 = vertx.setPeriodic(2000, this::handler);
        System.out.println("starting");
    }
    int count = 0;
    public void handler(long timerId) {
        System.out.println("timer "+timerId);
        if (++count>3)
            vertx.cancelTimer(timerId);
    }

    public void stop() {//Future<Void> stopFuture) {
        vertx.close();
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