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
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.BaggageUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.SemanticAttributes.MessagingDestinationKindValues;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Sends a persistent message to a queue using Solace JMS API implementation.
 * <p>
 * Setup a Solace PubSub+ Broker and OpenTelemetry Collector as per tutorial  >
 * https://codelabs.solace.dev/codelabs/dt-otel/index.html
 * <p>
 * The queue used for messages must have been created on the message broker.
 */
public class QueuePublisher {

	private static final String SERVICE_NAME = "ACME Product Master [DEV]";

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
		final String queueName = args[3];

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

			// Create the publishing queue programmatically
			final Queue messageDestination = session.createQueue(queueName);

			// Create the message producer for the created queue
			final MessageProducer messageProducer = session.createProducer(messageDestination);

			final TextMessage message = session.createTextMessage("Hello world!");
			final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
			final Tracer tracer = openTelemetry.getTracer(SERVICE_NAME);

			// Add few user properties
			message.setStringProperty("property1", "hello");
			message.setStringProperty("property2", "world");

			log("Sending message '%s' to queue '%s'...%n", message.getText(),
					messageDestination.getQueueName());

			traceAndPublish(message, messageProducer, messageDestination, openTelemetry, tracer);

			log("Sent successfully. Exiting...");
		}

		Thread.sleep(5000);
	}

	void traceAndPublish(Message message, MessageProducer messageProducer, Queue messageDestination,
			OpenTelemetry openTelemetry, Tracer tracer) throws JMSException {

		// Use the setter and propagator to inject OpenTelemetry info in the outgoing message
		final SolaceJmsW3CTextMapSetter setter = new SolaceJmsW3CTextMapSetter();
		final TextMapPropagator propagator = openTelemetry.getPropagators().getTextMapPropagator();

		// Spans are sections of code to instrument and identify. In this case creating a single 'send' span to cover the message publish.
		// (The span attributes are the details that get sent in each emitted span, should be consistent across applications.)

		final Span sendSpan = tracer
				.spanBuilder("Product Update > Send")    // The name as seen in the OTEL visualisation.
				.setSpanKind(SpanKind.PRODUCER)          // A broad identifier of the type of operation

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

				// Some transport attributes to include, in the SemanticAttributes name space:
				// See: https://opentelemetry.io/docs/specs/semconv/general/trace/

				.setAttribute(SemanticAttributes.MESSAGING_SYSTEM, "solace")
				.setAttribute(SemanticAttributes.MESSAGING_OPERATION, "send")
				.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_NAME, messageDestination.getQueueName())
				.setAttribute(SemanticAttributes.NET_PROTOCOL_NAME, "smf")

				.setParent(Context.current()) // set current context as parent (empty in this case, same as .setNoParent() )
				.startSpan();

		// This is signalling the span to have started, timestamps automatically captured.
		try (Scope scope = sendSpan.makeCurrent()) {

			// Add some OTEL Baggage (key-value store) of contextual information 
			// that can 'propagate' across multiple systems and spans by being copied from one to another.
			// See: https://opentelemetry.io/docs/concepts/signals/baggage/

			// This is actually carried as span attributes in a specific attribute naming range, 
			// however it is transparent to the recipient when the baggage is extracted.
			// i.e. No need to worry about a name space, just key names as needed by the downstream application(s).
			// Using the W3C Propagater, see below for key name rules and restrictions.
			// https://www.w3.org/TR/baggage/#key

			// Baggage in this case is the business data of product code and the operation that took place for it.
			// An operator can search the Observability tool by the product code, not needing to know about transport details.
			String productCode = "A001";
			String operation = "updated";
			String telemetryBaggageStr = "product_operation=" + operation + ",product_code=" + productCode;
			Baggage telemetryBaggage = BaggageUtil.extractBaggage(telemetryBaggageStr);

			// Store the baggage in the current OTEL context
			telemetryBaggage.storeInContext(Context.current()).makeCurrent();

			// Inject the Context (containing the send span and baggage) into the message
			propagator.inject(Context.current(), message, setter);

			// [Optional: for wider ecosystem compatibility...]
			// Insert the trace info as a message property to convey it to systems and protocols that do not support otel natively
			// i.e. Could be useful for internal logging for a receiver, or when creating onward spans manually and need the parent Trace ID.

			message.setStringProperty("otel_parent_trace_id", Span.current().getSpanContext().getTraceId());
			message.setStringProperty("otel_parent_span_id", Span.current().getSpanContext().getSpanId());
			message.setStringProperty("otel_parent_baggage", telemetryBaggageStr ); 


			// message is being published to the given topic
			messageProducer.send(messageDestination, message);

			System.out.println("Message sent, search for Trace ID: " + Span.current().getSpanContext().getTraceId());

		} catch (Exception e) {
			// Any exceptions in the send can also be captured in the span:
			sendSpan.recordException(e);
			sendSpan.setStatus(StatusCode.ERROR, e.getMessage());
			e.printStackTrace();
		} finally {
			// Mark the end of the span (instrumented section of code) by calling .end(). Data is then emitted.
			sendSpan.end();
		}
	}

	public static void main(String... args) throws Exception {
		// Check command line arguments
		if (args.length != 4 || args[1].split("@").length != 2) {
			log("Usage: QueuePublisher <host:port> <client-username@message-vpn> <client-password> <queue-name>");
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

		new QueuePublisher().run(args);
	}

	private static void log(String logMsg) {
		System.out.println(logMsg);
	}

	private static void log(String logMsg, Object... args) {
		System.out.println(String.format(logMsg, args));
	}
}