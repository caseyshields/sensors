# Extend vert.x image
FROM vertx/vertx3

#                                                       (1)
ENV MAIN_CLASS server.CaveServer
ENV JAR_FILE out/artifacts/sensors_jar/sensors.jar
ENV WEB_ROOT web

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles
ENV VERTICLE_ROOT /usr/verticles/web

EXPOSE 43210

# Copy your verticle to the container                   (2)
COPY $JAR_FILE $VERTICLE_HOME/

COPY $WEB_ROOT $VERTICLE_ROOT/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec vertx run $MAIN_CLASS -cp $VERTICLE_HOME/*"]

# build and run the image using;
#> docker build -t dune/vertx .
#> docker run -t -i -p 8080:8080 dune/vertx

# TODO deploying as a fat jar would make more sense for our prep like applications...