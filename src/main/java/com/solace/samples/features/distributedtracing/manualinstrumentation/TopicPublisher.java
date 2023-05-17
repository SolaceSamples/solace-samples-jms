/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.solace.samples.features.distributedtracing.manualinstrumentation;

import com.solace.opentelemetry.javaagent.jms.SolaceJmsW3CTextMapSetter;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingDestinationKindValues;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * Sends a persistent message to a queue using Solace JMS API implementation.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * This is the Publisher in the Publish-Subscribe messaging pattern.
 */
public class TopicPublisher {

  final String TOPIC_NAME = "solace/samples/jms/direct/pub/tracing";
  private static final String SERVICE_NAME = "SolaceJMSTopicPublisherManualInstrument";

  static {
    //Setup OpenTelemetry
    TracingUtil.initManualTracing(SERVICE_NAME);
  }

  public void run(String... args) throws Exception {
    final String[] split = args[1].split("@");

    final String host = args[0];
    final String vpnName = split[1];
    final String username = split[0];
    final String password = args[2];

    // Programmatically create the connection factory using default settings
    final SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setVPN(vpnName);
    connectionFactory.setUsername(username);
    connectionFactory.setPassword(password);

    try (final Connection connection = connectionFactory.createConnection();
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

      log("Connected to the Solace Message VPN '%s' with client username '%s'.%n", vpnName,
          username);

      // Create the publishing topic programmatically
      final Topic messageDestination = session.createTopic(TOPIC_NAME);

      // Create the message producer for the created topic
      final MessageProducer messageProducer = session.createProducer(messageDestination);

      final TextMessage message = session.createTextMessage("Hello world!");
      final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
      final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

      // Add few user properties
      message.setStringProperty("property1", "hello");
      message.setStringProperty("property2", "world");

      log("Sending message '%s' to topic '%s'...%n", message.getText(),
          messageDestination.getTopicName());

      traceAndPublish(message, messageProducer, messageDestination, openTelemetry, tracer);

      log("Sent successfully. Exiting...");
    }

    Thread.sleep(5000);
  }

  void traceAndPublish(Message message, MessageProducer messageProducer, Topic messageDestination,
      OpenTelemetry openTelemetry, Tracer tracer) throws JMSException {
    final Span sendSpan = tracer
        .spanBuilder("mySolacePublisherApp > send")
        .setSpanKind(SpanKind.CLIENT)
        // Optional: user defined Span attributes
        .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "SolacePubSub+")
        .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "send")
        .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, messageDestination.getTopicName())
        .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
            MessagingDestinationKindValues.TOPIC)
        .setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, false)
        .setParent(Context.current()) // set current context as parent
        .startSpan();

    try (final Scope scope = sendSpan.makeCurrent()) {
      final SolaceJmsW3CTextMapSetter setter = new SolaceJmsW3CTextMapSetter();
      final TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();
      //and then inject current context with send span into the message
      propagator.inject(Context.current(), message, setter);
      // message is being published to the given topic
      messageProducer.send(messageDestination, message);
    } catch (Exception e) {
      e.printStackTrace();
      sendSpan.setStatus(StatusCode.ERROR, e.getMessage());
    } finally {
      sendSpan.end();
    }
  }

  public static void main(String... args) throws Exception {
    // Check command line arguments
    if (args.length != 3 || args[1].split("@").length != 2) {
      log("Usage: TopicPublisher <host:port> <client-username@message-vpn> <client-password>");
      log("");
      System.exit(-1);
    }
    if (args[1].split("@")[0].isEmpty()) {
      log("No client-username entered");
      log("");
      System.exit(-1);
    }
    if (args[1].split("@")[1].isEmpty()) {
      log("No message-vpn entered");
      log("");
      System.exit(-1);
    }

    new TopicPublisher().run(args);
  }

  private static void log(String logMsg) {
    System.out.println(logMsg);
  }

  private static void log(String logMsg, Object... args) {
    System.out.println(String.format(logMsg, args));
  }
}