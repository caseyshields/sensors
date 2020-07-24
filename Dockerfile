# Extend vert.x image
FROM vertx/vertx3

#                                                       (1)
ENV VERTICLE_NAME server.HelloVerticle
ENV VERTICLE_FILE out/artifacts/sensors_jar/sensors.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

# Copy your verticle to the container                   (2)
COPY $VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec vertx run $VERTICLE_NAME -cp $VERTICLE_HOME/*"]

# build and run the image using;
#> docker build -t dune/vertx .
#> docker run -t -i -p 8080:8080 dune/vertx

# TODO deploying as a fat jar would make more sense for our prep like applications...