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
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

//OpenTelemetry Instrumentation Imports:
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.SemanticAttributes;

/**
 * Subscribes to message published to a Queue using Solace JMS 1.1 API implementation.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * This is the Subscriber in the Publish-Subscribe messaging pattern.
 */
public class QueueSubscriber {

  private static final String SERVICE_NAME = "Abc System [DEV]";

  // Latch used for synchronizing between threads
  final CountDownLatch latch = new CountDownLatch(1);

  static {
  	// "configure an instance of the OpenTelemetrySdk as early as possible in your application."
  	// Ref: https://opentelemetry.io/docs/languages/java/instrumentation/
      TracingUtil.initManualTracing(SERVICE_NAME);
  }

  public void run(String... args) throws Exception {
    final String[] split = args[1].split("@");

    final String host = args[0];
    final String vpnName = split[1];
    final String username = split[0];
    final String password = args[2];
    final String queueName = args[3];	        // Queue name read from supplied arguments

    log("QueueSubscriber is connecting to Solace messaging at %s...%n", host);

    // Programmatically create the connection factory using default settings
    final SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setVPN(vpnName);
    connectionFactory.setUsername(username);
    connectionFactory.setPassword(password);

    // Enables persistent queues or topic endpoints to be created dynamically
    // on the router, used when Session.createQueue() is called below
    connectionFactory.setDynamicDurables(true);
    // Remove this setting if the destination queue on the broker is configured to respect message TTLs
    connectionFactory.setRespectTTL(false);

    try (final Connection connection = connectionFactory.createConnection()) {
      // Create a non-transacted, Auto ACK session.
      final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      log("Connected to Solace Message VPN '%s' with client username '%s'.%n", vpnName, username);

      // Use the getter to extract OpenTelemetry context from the received message. (e.g. Parent Trace ID)
      // (It is always advised to extract context before injecting new one.) 
      // The SolaceJCSMPTextMapGetter handles extraction from Solace SMF messages that can embed OTEL.
      final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
      final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);
      
      // Create the queue programmatically and the corresponding router resource
      // will also be created dynamically because DynamicDurables is enabled.
      final Queue messageDestination = session.createQueue(queueName);

      // From the session, create a consumer for the destination.
      final MessageConsumer messageConsumer = session.createConsumer(messageDestination);

      //Sample messageProcessor callback
      final Consumer<Message> consoleLogger = (message) -> {
        log("New message received:%n%s%n", message.toString());
      };

      // Use the anonymous inner class for receiving messages asynchronously
      messageConsumer.setMessageListener(message -> {
        traceAndProcess(message, consoleLogger, messageDestination, openTelemetry, tracer);
        latch.countDown();
      });

      // Start receiving messages
      connection.start();
      log("Awaiting message...");

      // the main thread blocks at the next statement until a message received
      latch.await();
      connection.stop();
      messageConsumer.close();
      session.close();
    }
  }

  void traceAndProcess(Message receivedMessage, Consumer<Message> messageProcessor,
      Queue messageDestination, OpenTelemetry openTelemetry, Tracer tracer) {

	  
  	// Use the getter to extract OpenTelemetry context from the received message. (e.g. Parent Trace ID)
  	// (It is always advised to extract context before injecting new one.) 
  	// The SolaceJmsW3CTextMapGetter handles extraction from Solace JMS messages that can embed OTEL.	  
    final SolaceJmsW3CTextMapGetter getter = new SolaceJmsW3CTextMapGetter();
    
    final Context extractedContext = openTelemetry.getPropagators()
    		.getTextMapPropagator()
    		.extract(Context.current(), receivedMessage, getter);
    
    // Set the extracted context as current context as starting point
    try (Scope scope = extractedContext.makeCurrent()) {
    	
      // Create a child span to signal the message receive and set extracted/current context as parent of this span
      final Span receiveSpan = tracer
          .spanBuilder("Product Update > Received")		// The name as seen in the OTEL visualisation.
          .setSpanKind(SpanKind.CONSUMER)				// A broad identifier of the type of operation
          
          // Optional: user defined Span attributes
          // dot separated, snake_case is the convention, keeping to a fixed 'something.*' name space too for custom ones.
          // See: https://opentelemetry.io/docs/specs/semconv/general/attribute-naming/

          // Some runtime attributes to include:
          .setAttribute("env", "Development")
          .setAttribute("user.name", System.getProperty("user.name"))
          .setAttribute("java.version", System.getProperty("java.version"))
          .setAttribute("os.name", System.getProperty("os.name"))
          
          // Some transport attributes to include, in the SemanticAttributes name space:
          // See: https://opentelemetry.io/docs/specs/semconv/general/trace/

          .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
          .setAttribute(SemanticAttributes.MESSAGING_OPERATION, "receive")
          .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, receivedMessage.getJMSDestination().toString())
          .setAttribute(SemanticAttributes.NET_PROTOCOL_NAME, "smf")
          
          // Example attribute setting in a given namespace, information specific to this application 
          .setAttribute("com.acme.product_update.receive_key.1", "myValue1")
          //.setAttribute(...)
          
          // creates a parent child relationship to a message publisher's application span if any
          .setParent(extractedContext)
          .startSpan();

      // Any OTEL Baggage (business level meta data) propagated in the context?
      Baggage receivedTelemetryBaggage = Baggage.fromContext(extractedContext);
      String receivedTelemetryBaggageStr = "";

      for (Map.Entry<String, BaggageEntry> entry : receivedTelemetryBaggage.asMap().entrySet()) {
    	  receivedTelemetryBaggageStr = receivedTelemetryBaggageStr +
    			  entry.getKey() + "=" + receivedTelemetryBaggage.getEntryValue(entry.getKey()) + 
    			  ",";
      }

      System.out.println("Received a message with OTEL Trace ID: " + Span.current().getSpanContext().getTraceId() + 
    		  " with " + receivedTelemetryBaggage.size() + " keys found in telemetry baggage. " + receivedTelemetryBaggageStr);

      //... and then we do some processing and have another child span to signal that part of the code
      
      try {
          final Span processingSpan = tracer
              .spanBuilder("Product Update > Processed")    // The name as seen in the OTEL visualisation.
              .setSpanKind(SpanKind.SERVER)                 // Signalling this is internal server operation now

              // Set more attributes as needed for this part of the instrumentation
              .setAttribute("com.acme.product_update.processing_key.1", "postProcessingInformation")

              //.setAttribute(...)
              .setParent(Context.current().with(receiveSpan)) // make the RECEIVE span be the parent.
              .startSpan();
          
          	processingSpan.makeCurrent();

          	// Processing finished, ack the message and end this span
          	try {          		
          		messageProcessor.accept(receivedMessage);
          	} catch (Exception e) 
          	{
          		// Any exceptions in processing can also be captured in the span
          		processingSpan.recordException(e);
          		processingSpan.setStatus(StatusCode.ERROR, e.getMessage()); //Set span status as ERROR/FAILED
          	} finally {
            	// Mark the end of the span (instrumented section of code) by calling .end(). Data is then emitted.
                processingSpan.end(); //End processSpan. Span data is exported when span.end() is called.
          	}
      } catch (Exception e) {
    	  // Any exceptions in processing can also be captured in the span
    	  receiveSpan.recordException(e);
    	  receiveSpan.setStatus(StatusCode.ERROR, e.getMessage()); //Set span status as ERROR/FAILED
    	  e.printStackTrace();
      } finally {
    	// Mark the end of the parent span too by calling .end(). Data is then emitted.
    	receiveSpan.end(); 
      } 
      
    } catch (JMSException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}         	
  }

  public static void main(String... args) throws Exception {
    // Check command line arguments
    if (args.length != 4 || args[1].split("@").length != 2) {
      log("Usage: QueueConsumer <host:port> <client-username@message-vpn> <client-password> <queue-name>");
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
    final QueueSubscriber subscriber = new QueueSubscriber();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        Thread.sleep(1000);
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