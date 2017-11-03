#Ratpack-Zipkin Demo App

Demo Ratpack service using [ratpack-zipkin](https://github.com/hyleung/ratpack-zipkin) to add Zipkin tracing.

## Running the service

```
./gradlew clean run -DokHttpSenderEndPoint=http://localhost:9411/api/v1/spans
```

This will start the service and enable Zipkin tracing using the Scribe transport.
