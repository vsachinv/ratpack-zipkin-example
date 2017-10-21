package com.example.server;

import brave.sampler.Sampler;
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
                  .sampler(Sampler.create(samplingPct))
                  .spanReporter(spanReporter())
                  .spanNameProvider((request,pathBindingOpt) -> pathBindingOpt
                      .map(pathBinding -> pathBinding.getDescription())
                      .orElse(request.getPath()));
            })
            .bind(HelloWorldHandler.class)
            .add(MDCInterceptor.instance())
        ))
        .handlers(chain -> chain
            .get("hello", HelloWorldHandler.class)
            .get("say/:something", ctx -> ctx.render(String.format("Yo! %s", ctx.getPathTokens().get("something"))))
            .all(ctx -> ctx.render("root")))
    );

    RatpackServer.start(server -> server
        .serverConfig(config -> config.port(serverPort + 1))
        .registry(Guice.registry(binding -> binding
            .module(ServerTracingModule.class, config -> config
                .serviceName("other-server")
                .sampler(Sampler.create(samplingPct))
                .spanReporter(spanReporter())
                .spanNameProvider((request,pathBindingOpt) -> pathBindingOpt
                    .map(pathBinding -> pathBinding.getDescription())
                    .orElse(request.getPath())) )
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
