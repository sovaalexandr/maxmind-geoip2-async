package com.sovaalexandr;

import akka.stream.Materializer;
import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.ahc.StandaloneAhcWSClient;
import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import scala.collection.Seq;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Play not supplies such a module by default.
 */
public class StandaloneAhcWSModule extends Module
{

  @Override
  public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
    return seq(
      // AsyncHttpClientProvider is added by the Scala API
      bind(StandaloneWSClient.class).toProvider(JavaAhcWSClientProvider.class),
      bind(play.api.libs.ws.StandaloneWSClient.class).toProvider(ScalaAhcWSClientProvider.class)
    );
  }

  @Singleton
  public static class JavaAhcWSClientProvider implements Provider<StandaloneWSClient>
  {
    private final StandaloneWSClient client;

    @Inject
    public JavaAhcWSClientProvider(AsyncHttpClient asyncHttpClient, Materializer materializer) {
      client = new StandaloneAhcWSClient(asyncHttpClient, materializer);
    }

    @Override
    public StandaloneWSClient get() {
      return client;
    }
  }

  @Singleton
  public static class ScalaAhcWSClientProvider implements Provider<play.api.libs.ws.StandaloneWSClient>
  {
    private final play.api.libs.ws.StandaloneWSClient client;

    @Inject
    public ScalaAhcWSClientProvider(AsyncHttpClient asyncHttpClient, Materializer materializer) {
      client = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer);
    }

    @Override
    public play.api.libs.ws.StandaloneWSClient get() {
      return client;
    }
  }
}
