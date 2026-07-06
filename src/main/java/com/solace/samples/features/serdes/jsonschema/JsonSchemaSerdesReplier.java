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
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This sample demonstrates how to use the Solace JMS API with JSON Schema serialization and deserialization
 * for the Request-Reply messaging pattern. It connects to a Solace message broker, receives a CreateUser
 * request, deserializes it into a Plain Old Java Object using the {@link JsonSchemaDeserializer}, creates a
 * CreateUserResponse, serializes it using the {@link JsonSchemaSerializer}, and sends it back to the requestor.
 *
 * <p>This is the Replier in the Request/Reply messaging pattern. It is designed to be used with the
 * {@link JsonSchemaSerdesRequestor} sample.
 *
 * <p>Before running this sample, you must upload the create-user.json and create-user-response.json
 * schemas to the Solace Schema Registry with artifact IDs "solace/samples/create-user/json" and
 * "solace/samples/create-user-response/json" respectively.
 *
 * <p>Usage: JsonSchemaSerdesReplier &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
 *
 * <p>Environment variables for Schema Registry configuration:
 * <ul>
 *   <li>REGISTRY_URL - Schema Registry URL (default: http://localhost:8081/apis/registry/v3)</li>
 *   <li>REGISTRY_USERNAME - Schema Registry username (default: sr-readonly)</li>
 *   <li>REGISTRY_PASSWORD - Schema Registry password (default: roPassword)</li>
 * </ul>
 */
public class JsonSchemaSerdesReplier {

    private static final String SAMPLE_NAME = JsonSchemaSerdesReplier.class.getSimpleName();
    private static final String REQUEST_TOPIC = "solace/samples/create-user/json";
    // Logical schema subject used for Schema Registry resolution of the reply; independent of the
    // JMS reply destination (the requestor's TemporaryQueue), which has a dynamically generated name.
    private static final String REPLY_TOPIC = "solace/samples/create-user-response/json";
    private static final String API = "JMS";

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

        // Create and configure the JSON Schema deserializer (for requests) and serializer (for replies)
        try (Deserializer<CreateUser> deserializer = new JsonSchemaDeserializer<>();
             Serializer<CreateUserResponse> serializer = new JsonSchemaSerializer<>();
             Connection connection = connectionFactory.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            deserializer.configure(getConfig());
            serializer.configure(getConfig());

            Topic requestTopic = session.createTopic(REQUEST_TOPIC);

            // Create the message consumer for the request topic
            MessageConsumer requestConsumer = session.createConsumer(requestTopic);

            // Create the message producer used to send replies (destination supplied per reply)
            final MessageProducer replyProducer = session.createProducer(null);
            replyProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Receive request messages asynchronously
            requestConsumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message request) {
                    try {
                        Destination replyDestination = request.getJMSReplyTo();
                        if (replyDestination == null) {
                            System.out.println("Received message without reply-to field.");
                            return;
                        }

                        // Deserialize the request message
                        CreateUser createUserRequest =
                                deserializer.deserialize(REQUEST_TOPIC, readPayload(request), readHeaders(request));
                        System.out.printf("<< Received Request: %s%n", createUserRequest);

                        String name = createUserRequest.getName();
                        String email = createUserRequest.getEmail();
                        System.out.printf("Processing user creation request for: %s (%s)%n", name, email);

                        // Create a CreateUserResponse with a generated ID
                        CreateUserResponse createUserResponse = new CreateUserResponse();
                        String userId = UUID.randomUUID().toString().substring(0, 8);
                        createUserResponse.setId(userId);

                        // Serialize the response using the JSON Schema serializer. The subject is a fixed logical
                        // schema name for Schema Registry resolution, independent of the reply destination.
                        Map<String, Object> headers = new HashMap<>();
                        byte[] payloadBytes = serializer.serialize(REPLY_TOPIC, createUserResponse, headers);

                        BytesMessage reply = session.createBytesMessage();
                        reply.writeBytes(payloadBytes);

                        // Copy the correlation ID from the request to the reply
                        reply.setJMSCorrelationID(request.getJMSCorrelationID());

                        // For direct messaging only, this flag is needed to interoperate with
                        // Solace Java, C, and C# request reply APIs.
                        reply.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE, Boolean.TRUE);

                        // Set schema registry headers as message properties
                        for (Map.Entry<String, Object> entry : headers.entrySet()) {
                            reply.setObjectProperty(entry.getKey(), entry.getValue());
                        }

                        // Send the reply
                        replyProducer.send(replyDestination, reply);
                        System.out.printf(">> Sent Reply with user ID: %s%n", userId);
                    } catch (JsonSchemaValidationException ve) {
                        // Handle cases where a message fails validation against the schema.
                        System.out.println("Validation error: " + ve.getMessage());
                    } catch (Exception e) {
                        System.out.printf("Error processing received message: %s%n", e);
                    }
                }
            });

            // Start receiving messages
            connection.start();
            System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");

            // The async message listener serves requests until the main thread is unblocked by ENTER
            System.in.read();
        } // Auto-closes the deserializer, serializer, and connection

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
        new JsonSchemaSerdesReplier().run(args);
    }
}
