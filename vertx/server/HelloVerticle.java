package server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.Option;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;


public class HelloVerticle extends AbstractVerticle {

    private HttpServer server;

    public void start(){//Promise<Void> startPromise) {
//        context();
//        httpServer(startPromise);
//        timers();
//        eventBus();
        staticServer();
    }

    public void cli() {
        CLI cli = CLI.create("prep")
                .setSummary("A command line interface for launching preps")
                .addOption(new Option()
                        .setRequired(true)
                        .setLongName("input")
                        .setShortName("I")
                        .setDescription("sets the input directory"))
                .addOption(new Option()
                        .setRequired(true)
                        .setLongName("output")
                        .setShortName("O")
                        .setDescription("sets the ouput directory"))
                .addOption(new Option()
                        .setRequired(false)
                        .setLongName("pattern")
                        .setShortName("P")
                        .setDescription("sets the pattern for filtering files in the input directory"));
//                .addArgument(new Argument()
//                    .setIndex(0)
//                    .setDescription("")
//                    .setArgName(""));
    }
    public void ssl() {
        NetServerOptions options = new NetServerOptions().setSsl(true).setPemKeyCertOptions(
            new PemKeyCertOptions().
                setKeyPath("/path/to/your/server-key.pem").
                setCertPath("/path/to/your/server-cert.pem")
        );
        NetServer server = vertx.createNetServer(options);
    }

    public void staticServer() {
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        StaticHandler handler = StaticHandler.create()
                .setWebRoot("./web/")
                .setIncludeHidden(false)
                .setFilesReadOnly(false);
        router.route("/*").handler(handler);

        server.requestHandler(router).listen(8080);
    }

    public void eventBus() {
        // set up some event bus handlers
        EventBus bus = vertx.eventBus();
        MessageConsumer<String> consumer = bus.consumer("example.test");
        consumer.handler( message->{
            System.out.println( message.address() );
            System.out.println( message.replyAddress() );
            message.headers().forEach( header->
                System.out.println( header.getKey()+'='+header.getValue() ));
            System.out.println(message.body());
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
        server = vertx.createHttpServer().requestHandler(req ->
            req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x!") );
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
        long timer1 = vertx.setTimer(1000, id->System.out.println("timer "+id) );
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