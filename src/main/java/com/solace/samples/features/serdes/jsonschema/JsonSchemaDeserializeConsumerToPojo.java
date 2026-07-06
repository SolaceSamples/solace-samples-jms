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

import com.solace.samples.serdes.jsonschema.User;
import com.solace.serdes.Deserializer;
import com.solace.serdes.common.resolver.config.SchemaResolverProperties;
import com.solace.serdes.jsonschema.JsonSchemaDeserializer;
import com.solace.serdes.jsonschema.JsonSchemaProperties;
import com.solace.serdes.jsonschema.JsonSchemaValidationException;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * This sample demonstrates how to use Solace JMS API with JSON Schema deserialization to consume a message
 * and deserialize its payload to a Plain Old Java Object (POJO).
 * The JSON schema (user.json) being deserialized contains the 'customJavaType' property, which specifies the target
 * class for deserialization. Refer to the {@link JsonSchemaProperties#TYPE_PROPERTY} configuration being done
 * in {@link JsonSchemaDeserializeConsumerToPojo#getConfig()}.
 *
 * <p>This consumer is designed to be used with the JsonSchemaSerializeProducer sample.
 *
 * <p>Before running this sample, you must upload the user.json schema to the Solace Schema Registry
 * with artifact ID "solace/samples/json".
 *
 * <p>Usage: JsonSchemaDeserializeConsumerToPojo &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
 *
 * <p>Environment variables for Schema Registry configuration:
 * <ul>
 *   <li>REGISTRY_URL - Schema Registry URL (default: http://localhost:8081/apis/registry/v3)</li>
 *   <li>REGISTRY_USERNAME - Schema Registry username (default: sr-readonly)</li>
 *   <li>REGISTRY_PASSWORD - Schema Registry password (default: roPassword)</li>
 * </ul>
 */
public class JsonSchemaDeserializeConsumerToPojo {

    private static final String SAMPLE_NAME = JsonSchemaDeserializeConsumerToPojo.class.getSimpleName();
    private static final String TOPIC_NAME = "solace/samples/json";
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
        connectionFactory.setDirectTransport(false);
        connectionFactory.setClientID(API + "_" + SAMPLE_NAME);

        // Create and configure JSON Schema deserializer
        try (Deserializer<User> deserializer = new JsonSchemaDeserializer<>();
             Connection connection = connectionFactory.createConnection()) {

            deserializer.configure(getConfig());

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(TOPIC_NAME);

            // Create the message consumer for the subscription topic
            MessageConsumer messageConsumer = session.createConsumer(topic);

            // Start receiving messages
            connection.start();

            System.out.println("Awaiting message...");
            // The current thread blocks at the next statement until a message arrives
            Message message = messageConsumer.receive();

            if (message == null) {
                System.out.println("Consumer closed before a message was received.");
                return;
            }

            // Process received message
            byte[] payloadBytes;
            Map<String, Object> headers = new HashMap<>();

            if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;

                // Read the payload bytes
                payloadBytes = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(payloadBytes);
            } else if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;

                // Convert text to bytes
                payloadBytes = textMessage.getText().getBytes(StandardCharsets.UTF_8);
            } else {
                System.out.println("Unexpected message type received: " + message.getClass().getName());
                return;
            }

            // Extract headers from message properties
            Enumeration<?> propertyNames = message.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String propertyName = (String) propertyNames.nextElement();
                headers.put(propertyName, message.getObjectProperty(propertyName));
            }

            // Deserialize the received message
            // Note: the 'customJavaType' property in the schema specifies the 'User' class, so the deserializer returns a User object.
            User user = deserializer.deserialize(TOPIC_NAME, payloadBytes, headers);

            System.out.println("Received JSON schema message: " + user);
        } catch (JsonSchemaValidationException ve) {
            // Handle cases where the message fails validation against the schema.
            // This could happen if the schema in the registry is different from what is expected.
            System.out.println("Validation error: " + ve.getMessage());
        } // Auto-closes the deserializer and connection
    }

    /**
     * Returns a configuration map for the JSON Schema deserializer.
     *
     * @return A Map containing configuration properties for the Schema Registry
     */
    private static Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaResolverProperties.REGISTRY_URL, REGISTRY_URL);
        config.put(SchemaResolverProperties.AUTH_USERNAME, REGISTRY_USERNAME);
        config.put(SchemaResolverProperties.AUTH_PASSWORD, REGISTRY_PASSWORD);
        config.put(JsonSchemaProperties.TYPE_PROPERTY, "customJavaType");
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
        new JsonSchemaDeserializeConsumerToPojo().run(args);
    }
}
