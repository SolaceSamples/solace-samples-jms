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

package com.solace.samples.features.serdes.jsonschema;

import com.solace.samples.serdes.jsonschema.CreateUser;
import com.solace.samples.serdes.jsonschema.CreateUserResponse;
import com.solace.serdes.Deserializer;
import com.solace.serdes.Serializer;
import com.solace.serdes.common.resolver.config.SchemaResolverProperties;
import com.solace.serdes.jsonschema.JsonSchemaDeserializer;
import com.solace.serdes.jsonschema.JsonSchemaProperties;
import com.solace.serdes.jsonschema.JsonSchemaSerializer;
import com.solace.serdes.jsonschema.JsonSchemaValidationException;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This sample demonstrates how to use the Solace JMS API with JSON Schema serialization and deserialization
 * for the Request-Reply messaging pattern. It connects to a Solace message broker, serializes a CreateUser
 * request (a Plain Old Java Object) using the {@link JsonSchemaSerializer}, publishes it to a request topic,
 * waits for a reply, and then deserializes the CreateUserResponse reply.
 *
 * <p>This is the Requestor in the Request/Reply messaging pattern. It is designed to be used with the
 * {@link JsonSchemaSerdesReplier} sample.
 *
 * <p>Before running this sample, you must upload the create-user.json and create-user-response.json
 * schemas to the Solace Schema Registry with artifact IDs "solace/samples/create-user/json" and
 * "solace/samples/create-user-response/json" respectively.
 *
 * <p>Usage: JsonSchemaSerdesRequestor &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
 *
 * <p>Environment variables for Schema Registry configuration:
 * <ul>
 *   <li>REGISTRY_URL - Schema Registry URL (default: http://localhost:8081/apis/registry/v3)</li>
 *   <li>REGISTRY_USERNAME - Schema Registry username (default: sr-readonly)</li>
 *   <li>REGISTRY_PASSWORD - Schema Registry password (default: roPassword)</li>
 * </ul>
 */
public class JsonSchemaSerdesRequestor {

    private static final String SAMPLE_NAME = JsonSchemaSerdesRequestor.class.getSimpleName();
    private static final String REQUEST_TOPIC = "solace/samples/create-user/json";
    // Logical schema subject used for Schema Registry resolution of the reply; independent of the
    // JMS reply destination (a private TemporaryQueue), which has a dynamically generated name.
    private static final String REPLY_TOPIC = "solace/samples/create-user-response/json";
    private static final String API = "JMS";

    private static final int REQUEST_TIMEOUT_MS = 10000; // 10 seconds

    private static final String REGISTRY_URL = getEnv("REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
    private static final String REGISTRY_USERNAME = getEnv("REGISTRY_USERNAME", "sr-readonly");
    private static final String REGISTRY_PASSWORD = getEnv("REGISTRY_PASSWORD", "roPassword");

    private void run(String... args) throws Exception {
        String host = args[0];
        String vpn = args[1];
        String clientUsername = args[2];
        String password = args.length > 3 ? args[3] : null;

        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setVPN(vpn);
        connectionFactory.setUsername(clientUsername);
        if (password != null) {
            connectionFactory.setPassword(password);
        }
        connectionFactory.setClientID(API + "_" + SAMPLE_NAME);

        // Create and configure the JSON Schema serializer (for requests) and deserializer (for replies)
        try (Serializer<CreateUser> serializer = new JsonSchemaSerializer<>();
             Deserializer<CreateUserResponse> deserializer = new JsonSchemaDeserializer<>();
             Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            serializer.configure(getConfig());
            deserializer.configure(getConfig());

            Topic requestTopic = session.createTopic(REQUEST_TOPIC);

            // Create the message producer for the request topic
            MessageProducer producer = session.createProducer(requestTopic);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // The reply will be received on this temporary queue, which is private to this requestor
            TemporaryQueue replyToQueue = session.createTemporaryQueue();
            MessageConsumer replyConsumer = session.createConsumer(replyToQueue);

            // Start receiving messages
            connection.start();

            // Create and populate a CreateUser POJO with sample data
            CreateUser userRequest = new CreateUser();
            userRequest.setName("John Doe");
            userRequest.setEmail("support@solace.com");

            try {
                // Serialize the request POJO using the JSON Schema serializer
                Map<String, Object> headers = new HashMap<>();
                byte[] payloadBytes = serializer.serialize(REQUEST_TOPIC, userRequest, headers);

                // Create a BytesMessage with the serialized payload
                BytesMessage requestMessage = session.createBytesMessage();
                requestMessage.writeBytes(payloadBytes);

                // The application must put the destination of the reply in the replyTo field of the request
                requestMessage.setJMSReplyTo(replyToQueue);
                // The application must put a correlation ID in the request
                String correlationId = UUID.randomUUID().toString();
                requestMessage.setJMSCorrelationID(correlationId);

                // Set schema registry headers as message properties
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    requestMessage.setObjectProperty(entry.getKey(), entry.getValue());
                }

                System.out.printf(">> Sending Request: %s%n", userRequest);
                producer.send(requestMessage);
                System.out.println("Sent successfully. Waiting for reply...");

                // The main thread blocks at the next statement until a reply is received or the timeout occurs
                Message reply = replyConsumer.receive(REQUEST_TIMEOUT_MS);

                if (reply == null) {
                    throw new Exception("Failed to receive a reply in " + REQUEST_TIMEOUT_MS + " msecs");
                }

                // Validate the reply. The correlation ID must match the request; Apache Qpid JMS prefixes
                // the correlation ID with "ID:", so strip such prefix for interoperability.
                if (reply.getJMSCorrelationID() == null) {
                    throw new Exception("Received a reply message with no correlationID.");
                }
                if (!reply.getJMSCorrelationID().replaceFirst("ID:", "").equals(correlationId)) {
                    throw new Exception("Received invalid correlationID in reply message.");
                }
                // For direct messaging, this flag indicates interoperability with the Solace Java, C, and C# APIs
                if (!reply.getBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE)) {
                    System.err.println("Warning: Received a reply message without the isReplyMsg flag set.");
                }

                // Deserialize the reply message
                CreateUserResponse userResponse =
                        deserializer.deserialize(REPLY_TOPIC, readPayload(reply), readHeaders(reply));

                System.out.printf("<< Received Reply: %s%n", userResponse);
                System.out.printf("User created with ID: %s%n", userResponse.getId());
            } catch (JsonSchemaValidationException ve) {
                // Handle cases where a message fails validation against the schema.
                // This could happen if the schema in the registry is different from what is expected.
                System.out.println("Validation error: " + ve.getMessage());
            }
        } // Auto-closes the serializer, deserializer, and connection

        System.out.println("Exiting.");
    }

    /**
     * Reads the raw payload bytes from a received JMS message.
     *
     * @param message The received message
     * @return The payload bytes
     * @throws Exception If the message type is unsupported or the body cannot be read
     */
    private static byte[] readPayload(Message message) throws Exception {
        if (message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] payloadBytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(payloadBytes);
            return payloadBytes;
        } else if (message instanceof TextMessage) {
            return ((TextMessage) message).getText().getBytes(StandardCharsets.UTF_8);
        }
        throw new Exception("Unexpected message type received: " + message.getClass().getName());
    }

    /**
     * Extracts the schema registry headers from a received JMS message's properties.
     *
     * @param message The received message
     * @return A Map of the message properties
     * @throws Exception If the properties cannot be read
     */
    private static Map<String, Object> readHeaders(Message message) throws Exception {
        Map<String, Object> headers = new HashMap<>();
        Enumeration<?> propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements()) {
            String propertyName = (String) propertyNames.nextElement();
            headers.put(propertyName, message.getObjectProperty(propertyName));
        }
        return headers;
    }

    /**
     * Returns a base configuration map for the JSON Schema serializer.
     *
     * @return A Map containing configuration properties for the Schema Registry
     */
    private static Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaResolverProperties.REGISTRY_URL, REGISTRY_URL);
        config.put(SchemaResolverProperties.AUTH_USERNAME, REGISTRY_USERNAME);
        config.put(SchemaResolverProperties.AUTH_PASSWORD, REGISTRY_PASSWORD);
        return config;
    }

    /**
     * Gets an environment variable or returns a default value.
     *
     * @param name The name of the environment variable
     * @param defaultValue The default value to use if the environment variable is not set
     * @return The environment variable value or default value
     */
    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    public static void main(String... args) throws Exception {
        if (args.length < 3) {
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        new JsonSchemaSerdesRequestor().run(args);
    }
}
