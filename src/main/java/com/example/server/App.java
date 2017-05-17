package com.example.server;

import brave.http.HttpAdapter;
import brave.http.HttpSampler;
import ratpack.guice.Guice;
import ratpack.logging.MDCInterceptor;
import ratpack.server.RatpackServer;
import ratpack.zipkin.ServerTracingModule;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.kafka08.KafkaSender;
import zipkin.reporter.libthrift.LibthriftSender;

/**
 * RatPack Server.
 */
public class App {
  public static void main(String[] args) throws Exception {
    Integer serverPort = Integer.parseInt(System.getProperty("port", "8081"));
    Float samplingPct = Float.parseFloat(System.getProperty("samplingPct","1"));

    RatpackServer.start(server -> server
        .serverConfig(config -> config.port(serverPort))
        .registry(Guice.registry(binding -> binding
            .module(ServerTracingModule.class, config -> {
config
    .serviceName("ratpack-demo")
    .serverSampler(new HttpSampler() {
      @Override
      public <Req> Boolean trySample(final HttpAdapter<Req, ?> adapter, final Req request) {
        return true;
      }
    })
    .clientSampler(HttpSampler.TRACE_ID)
    .spanReporter(spanReporter());
            })
            .bind(HelloWorldHandler.class)
            .add(MDCInterceptor.instance())
        ))
        .handlers(chain -> chain
            .get("hello", HelloWorldHandler.class)
            .all(ctx -> ctx.render("root")))
    );

    RatpackServer.start(server -> server
        .serverConfig(config -> config.port(serverPort + 1))
        .registry(Guice.registry(binding -> binding
            .module(ServerTracingModule.class, config -> config
                .serviceName("other-server")
                .spanReporter(spanReporter()))
            .bind(HelloWorldHandler.class)
            .add(MDCInterceptor.instance())
        ))
        .handlers(chain -> chain
            .all(ctx -> ctx.render("root")))
    );
  }

  private static Reporter<Span> spanReporter() {
    String scribeHost = System.getProperty("scribeHost");
    String kafkaHost = System.getProperty("kafkaHost");
    if (scribeHost != null) {
      return AsyncReporter.builder(LibthriftSender.builder()
                                           .host(scribeHost)
                                           .port(9410)
                                           .build()).build();
    } else if (kafkaHost != null) {
      return AsyncReporter.builder(KafkaSender.builder()
          .bootstrapServers(kafkaHost)
          .topic("zipkin")
          .build()).build();
    } else {
      return Reporter.CONSOLE;
    }
  }

}
