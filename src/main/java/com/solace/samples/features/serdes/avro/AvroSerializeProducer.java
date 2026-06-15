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

import com.solace.serdes.Serializer;
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
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This sample demonstrates how to use the Solace JMS API with Avro serialization to produce messages.
 * It connects to a Solace message broker, serializes a User record using Avro from a generic record
 * (an Avro {@link GenericRecord}), and publishes a single message to a topic.
 *
 * <p>For the specific-record variant that serializes a generated Avro class, see
 * {@link AvroSerializeProducerSpecificRecord}.
 *
 * <p>This producer is designed to be used with the AvroDeserializeConsumer sample.
 *
 * <p>Before running this sample, you must upload the user.avsc schema to the Solace Schema Registry
 * with artifact ID "solace/samples/avro".
 *
 * <p>Usage: AvroSerializeProducer &lt;host:port&gt; &lt;message-vpn&gt; &lt;client-username&gt; [password]
 *
 * <p>Environment variables for Schema Registry configuration:
 * <ul>
 *   <li>REGISTRY_URL - Schema Registry URL (default: http://localhost:8081/apis/registry/v3)</li>
 *   <li>REGISTRY_USERNAME - Schema Registry username (default: sr-readonly)</li>
 *   <li>REGISTRY_PASSWORD - Schema Registry password (default: roPassword)</li>
 * </ul>
 */
public class AvroSerializeProducer {

    private static final String SAMPLE_NAME = AvroSerializeProducer.class.getSimpleName();
    private static final String TOPIC_NAME = "solace/samples/avro";
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

        // Create and configure Avro serializer
        try (Serializer<GenericRecord> serializer = new AvroSerializer<>();
             Connection connection = connectionFactory.createConnection()) {

            serializer.configure(getConfig());

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(TOPIC_NAME);

            // Create the message producer
            MessageProducer producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // Create a User GenericRecord from the Avro schema
            GenericRecord user = initEmptyUserRecord();
            user.put("id", "123");
            user.put("name", "John Doe");
            user.put("email", "support@solace.com");

            // Serialize the user record using the Avro serializer
            Map<String, Object> headers = new HashMap<>();
            byte[] payloadBytes = serializer.serialize(TOPIC_NAME, user, headers);

            // Create a BytesMessage with the serialized payload
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(payloadBytes);

            // Set schema registry headers as message properties
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                bytesMessage.setObjectProperty(entry.getKey(), entry.getValue());
            }

            producer.send(bytesMessage);
            System.out.printf(">> Sending User: %s%n", user);
        } // Auto-closes the serializer and connection

        System.out.println("Message sent. Exiting.");
    }

    /**
     * Initializes an empty Avro GenericRecord based on the "user.avsc" schema.
     *
     * @return An empty GenericRecord
     * @throws IOException If there's an error reading the schema file
     */
    private static GenericRecord initEmptyUserRecord() throws IOException {
        try (InputStream rawSchema = AvroSerializeProducer.class.getResourceAsStream("/avro-schema/user.avsc")) {
            Schema schema = new SchemaParser().parse(rawSchema).mainSchema();
            return new GenericData.Record(schema);
        }
    }

    /**
     * Returns a configuration map for the Avro serializer.
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
        new AvroSerializeProducer().run(args);
    }
}
