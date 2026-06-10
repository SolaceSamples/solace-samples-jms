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

package com.solace.samples.features.serdes.avro;

import com.solace.serdes.Deserializer;
import com.solace.serdes.Serializer;
import com.solace.serdes.avro.AvroDeserializer;
import com.solace.serdes.avro.AvroSerializer;
import com.solace.serdes.common.resolver.config.SchemaResolverProperties;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParser;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This sample demonstrates publishing and subscribing with Avro serialization and deserialization
 * using the Solace JMS API. It connects to a Solace message broker, publishes a User message serialized
 * with Avro, and receives and deserializes the message back to a GenericRecord.
 *
 * <p>Before running this sample, you must upload the user.avsc schema to the Solace Schema Registry
 * with artifact ID "solace/samples/avro".
 *
 * <p>Usage: HelloWorldJmsAvroSerde &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
 *
 * <p>Environment variables for Schema Registry configuration:
 * <ul>
 *   <li>REGISTRY_URL - Schema Registry URL (default: http://localhost:8081/apis/registry/v3)</li>
 *   <li>REGISTRY_USERNAME - Schema Registry username (default: sr-readonly)</li>
 *   <li>REGISTRY_PASSWORD - Schema Registry password (default: roPassword)</li>
 * </ul>
 */
public class HelloWorldJmsAvroSerde {

    private static final String SAMPLE_NAME = HelloWorldJmsAvroSerde.class.getSimpleName();
    private static final String TOPIC_NAME = "solace/samples/avro";
    private static final String API = "JMS";

    private static final String REGISTRY_URL = getEnv("REGISTRY_URL", "http://localhost:8081/apis/registry/v3");
    private static final String REGISTRY_USERNAME = getEnv("REGISTRY_USERNAME", "sr-readonly");
    private static final String REGISTRY_PASSWORD = getEnv("REGISTRY_PASSWORD", "roPassword");

    /**
     * The main method that demonstrates the Solace JMS API usage with Avro serialization/deserialization.
     *
     * @param args Command line arguments: &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
     * @throws Exception If any error occurs during execution
     */
    public static void main(String... args) throws Exception {
        if (args.length < 3) {
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }

        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        // Create a latch to synchronize the main thread with the message consumer
        CountDownLatch latch = new CountDownLatch(1);

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(args[0]);          // host:port
        connectionFactory.setVPN(args[1]);           // message-vpn
        connectionFactory.setUsername(args[2]);      // client-username
        if (args.length > 3) {
            connectionFactory.setPassword(args[3]);  // client-password
        }
        connectionFactory.setDirectTransport(false);     // use Guaranteed transport for "non-persistent" messages
        connectionFactory.setClientID(API + "_" + SAMPLE_NAME);

        // Create and configure Avro serializer and deserializer
        try (Serializer<GenericRecord> serializer = new AvroSerializer<>();
             Deserializer<GenericRecord> deserializer = new AvroDeserializer<>();
             Connection connection = connectionFactory.createConnection()) {

            serializer.configure(getConfig());
            deserializer.configure(getConfig());

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(TOPIC_NAME);

            // Set up the message consumer with deserialization
            MessageConsumer consumer = session.createConsumer(topic);
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        byte[] payloadBytes;

                        if (message instanceof BytesMessage) {
                            BytesMessage bytesMessage = (BytesMessage) message;
                            payloadBytes = new byte[(int) bytesMessage.getBodyLength()];
                            bytesMessage.readBytes(payloadBytes);
                        } else if (message instanceof TextMessage) {
                            TextMessage textMessage = (TextMessage) message;
                            payloadBytes = textMessage.getText().getBytes(StandardCharsets.UTF_8);
                        } else {
                            System.out.println("Unexpected message type: " + message.getClass().getName());
                            return;
                        }

                        // Extract headers from message properties
                        Map<String, Object> headers = new HashMap<>();
                        Enumeration<?> propertyNames = message.getPropertyNames();
                        while (propertyNames.hasMoreElements()) {
                            String propertyName = (String) propertyNames.nextElement();
                            headers.put(propertyName, message.getObjectProperty(propertyName));
                        }

                        // Deserialize the message using Avro deserializer
                        GenericRecord user = deserializer.deserialize(TOPIC_NAME, payloadBytes, headers);
                        System.out.printf("vvv RECEIVED A MESSAGE vvv%n");
                        System.out.printf("Received User: name=%s, id=%s, email=%s%n",
                                user.get("name"),
                                user.get("id"),
                                user.get("email"));
                    } catch (Exception e) {
                        System.out.printf("Error processing received message: %s%n", e);
                    } finally {
                        latch.countDown();
                    }
                }
            });

            connection.start();  // start receiving messages

            // Create producer and send a serialized message
            MessageProducer producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Create a User GenericRecord from the Avro schema
            GenericRecord user = initEmptyUserRecord();
            user.put("name", "John Doe");
            user.put("id", "123");
            user.put("email", "support@solace.com");

            // Serialize the user object using Avro serializer
            Map<String, Object> headers = new HashMap<>();
            byte[] payloadBytes = serializer.serialize(TOPIC_NAME, user, headers);

            // Create a BytesMessage with the serialized payload
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(payloadBytes);

            // Set schema registry headers as message properties
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                bytesMessage.setObjectProperty(entry.getKey(), entry.getValue());
            }

            System.out.printf(">> Sending User: %s%n", user);
            producer.send(bytesMessage);

            // Wait for the consumer to receive the message
            latch.await();
        } // Try block Auto-closes the serializer, deserializer, and connection

        System.out.println("Main thread quitting.");
    }

    /**
     * Initializes an empty Avro GenericRecord based on the "user.avsc" schema.
     *
     * @return An empty GenericRecord
     * @throws IOException If there's an error reading the schema file
     */
    private static GenericRecord initEmptyUserRecord() throws IOException {
        try (InputStream rawSchema = HelloWorldJmsAvroSerde.class.getResourceAsStream("/avro-schema/user.avsc")) {
            Schema schema = new SchemaParser().parse(rawSchema).mainSchema();
            return new GenericData.Record(schema);
        }
    }

    /**
     * Returns a configuration map for the Avro serializer and deserializer.
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
}
