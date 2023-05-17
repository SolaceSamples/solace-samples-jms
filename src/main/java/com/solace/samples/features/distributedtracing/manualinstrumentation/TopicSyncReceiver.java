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

import com.solace.opentelemetry.javaagent.jms.SolaceJmsW3CTextMapGetter;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingDestinationKindValues;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.MessagingOperationValues;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Topic;

/**
 * Subscribes to message published to a topic using Solace JMS 1.1 API implementation.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * This is the Subscriber in the Publish-Subscribe messaging pattern.
 */
public class TopicSyncReceiver {

  private static final String TOPIC_NAME = "solace/samples/jms/direct/pub/tracing";
  private static final String SERVICE_NAME = "SolaceJMSTopicSyncSubscriberManualInstrument";

  // Latch used for synchronizing between threads
  private final CountDownLatch latch = new CountDownLatch(1);

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

    log("TopicSyncReceiver is connecting to Solace messaging at %s...%n", host);

    // Programmatically create the connection factory using default settings
    final SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setVPN(vpnName);
    connectionFactory.setUsername(username);
    connectionFactory.setPassword(password);

    // Enables persistent queues or topic endpoints to be created dynamically
    // on the router, used when Session.createTopic() is called below
    connectionFactory.setDynamicDurables(true);

    try (final Connection connection = connectionFactory.createConnection()) {
      // Create a non-transacted, Auto ACK session.
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      log("Connected to Solace Message VPN '%s' with client username '%s'.%n", vpnName, username);

      // Create the subscription topic programmatically
      final Topic messageDestination = session.createTopic(TOPIC_NAME);

      // Create the message consumer for the subscription topic
      final MessageConsumer messageConsumer = session.createConsumer(messageDestination);
      // Start receiving messages
      connection.start();

      final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
      final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

      //Sample messageProcessor callback
      final Consumer<Message> consoleLogger = (message) -> {
        log("New message received:%n%s%n", SolJmsUtility.dumpMessage(message));
      };

      // Receiving messages synchronously
      final Message message = messageConsumer.receive();
      try {
        if (message != null) {
          traceAndProcess(message, consoleLogger, messageDestination, openTelemetry, tracer);
        }
      } finally {
        latch.countDown(); // unblock the main thread
      }

      connection.stop();
      messageConsumer.close();
      session.close();
    }
  }

  void traceAndProcess(Message receivedMessage, Consumer<Message> messageProcessor,
      Topic messageDestination, OpenTelemetry openTelemetry, Tracer tracer) {

    //Extract tracing context from message, if any using the SolaceJmsW3CTextMapGetter
    final SolaceJmsW3CTextMapGetter getter = new SolaceJmsW3CTextMapGetter();
    final Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
        .extract(Context.current(), receivedMessage, getter);

    //Set the extracted context as current context
    try (final Scope scope = extractedContext.makeCurrent()) {
      //Create a child span and set extracted/current context as parent of this span
      final Span receiveSpan = tracer
          .spanBuilder("mySolaceReceiverApp" + " " + MessagingOperationValues.PROCESS)
          .setSpanKind(SpanKind.CLIENT)
          .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "SolacePubSub+")
          .setAttribute(SemanticAttributes.MESSAGING_OPERATION, MessagingOperationValues.PROCESS)
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, messageDestination.getTopicName())
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND,
              MessagingDestinationKindValues.TOPIC)
          .setAttribute(SemanticAttributes.MESSAGING_TEMP_DESTINATION, false)
          // creates a parent child relationship to a message publisher's application span if any
          .setParent(extractedContext)
          .startSpan();

      try {
        messageProcessor.accept(receivedMessage);
      } finally {
        receiveSpan.end();
      }
    } catch (JMSException e) {
      e.printStackTrace();
    }
  }

  public static void main(String... args) throws Exception {
    // Check command line arguments
    if (args.length != 3 || args[1].split("@").length != 2) {
      log("Usage: TopicSyncReceiver <host:port> <client-username@message-vpn> <client-password>");
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
    final TopicSyncReceiver subscriber = new TopicSyncReceiver();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        Thread.sleep(100);
        log("Shutting down ...");
        subscriber.latch.countDown();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        e.printStackTrace();
      }
    }));
    subscriber.run(args);
  }

  private static void log(String logMsg) {
    System.out.println(logMsg);
  }

  private static void log(String logMsg, Object... args) {
    System.out.println(String.format(logMsg, args));
  }
}