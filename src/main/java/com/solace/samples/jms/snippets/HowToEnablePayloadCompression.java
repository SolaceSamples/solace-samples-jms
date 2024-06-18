/*
 * Copyright 2021-2023 Solace Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.solace.samples.jms.snippets;

import com.solacesystems.jms.SupportedProperty;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * Sample of how to enable Payload Compression in JMS.
 */
public class HowToEnablePayloadCompression {
    final static String SOLJMS_INITIAL_CONTEXT_FACTORY = "com.solacesystems.jndi.SolJNDIInitialContextFactory";
    final static String CONNECTION_FACTORY_JNDI_NAME = "/JNDI/CF/GettingStarted";
    final static String TOPIC_NAME = "T/GettingStarted/PayloadCompression";

    /**
     * Sample of how to create an {@code InitialContext} with payload compression enabled. Note: this is only when connecting to old
     * brokers that do not have payloadCompressionLevel in the connection factory. Brokers with JMS payload compression
     * in the connection factory WILL OVERWRITE this value.
     *
     * @param jndiProviderURL The URL to the solace broker.
     * @param username The username to connect to the solace broker.
     * @param payloadCompressionLevel A compression level the range 0-9.
     *     <p>Value meanings:
     *     <ul>
     *       <li>0 - disable payload compression (the default)
     *       <li>1 - least amount of compression and fastest data throughput
     *       <li>9 - most compression and slowest data throughput
     *     </ul>
     *
     * @return An {@code InitialContext} instance with payload compression enabled
     *
     * @see SupportedProperty#SOLACE_JMS_PAYLOAD_COMPRESSION_LEVEL
     */
    public static InitialContext createInitialContextWithPayloadCompression(String jndiProviderURL, String username, int payloadCompressionLevel) throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, SOLJMS_INITIAL_CONTEXT_FACTORY);
        env.put(InitialContext.PROVIDER_URL, jndiProviderURL);
        env.put(Context.SECURITY_PRINCIPAL, username);

        env.put(SupportedProperty.SOLACE_JMS_PAYLOAD_COMPRESSION_LEVEL, payloadCompressionLevel);

        // Other properties
        // Create InitialContext.
        return new InitialContext(env);
    }

    /**
     * Sample of how to enable compressed payload to send a {@code BytesMessage}.
     *
     * @param jndiProviderURL The URL to the solace broker.
     * @param username The username to connect to the solace broker.
     * @param payloadCompressionLevel A compression level the range 0-9.
     *     <p>Value meanings:
     *     <ul>
     *       <li>0 - disable payload compression (the default)
     *       <li>1 - least amount of compression and fastest data throughput
     *       <li>9 - most compression and slowest data throughput
     *     </ul>
     *
     * @see SupportedProperty#SOLACE_JMS_PAYLOAD_COMPRESSION_LEVEL
     */
    public static void sendCompressedBytesMessage(String jndiProviderURL,String username, int payloadCompressionLevel) throws NamingException, JMSException {
        InitialContext initialContext = createInitialContextWithPayloadCompression(jndiProviderURL, username, payloadCompressionLevel);

        ConnectionFactory cf = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(TOPIC_NAME);
        MessageProducer producer = session.createProducer(topic);

        BytesMessage msg = session.createBytesMessage();
        msg.writeBytes(getCompressibleMessage().getBytes());

        producer.send(msg);
    }

    /**
     * Sample of how to enable compressed payload to send a {@code TextMessage}.
     *
     * @param jndiProviderURL The URL to the solace broker.
     * @param username The username to connect to the solace broker.
     * @param payloadCompressionLevel A compression level the range 0-9.
     *     <p>Value meanings:
     *     <ul>
     *       <li>0 - disable payload compression (the default)
     *       <li>1 - least amount of compression and fastest data throughput
     *       <li>9 - most compression and slowest data throughput
     *     </ul>
     *
     * @see SupportedProperty#SOLACE_JMS_PAYLOAD_COMPRESSION_LEVEL
     */
    public static void sendCompressedTextMessage(String jndiProviderURL, String username, int payloadCompressionLevel) throws NamingException, JMSException {
        InitialContext initialContext = createInitialContextWithPayloadCompression(jndiProviderURL, username, payloadCompressionLevel);

        ConnectionFactory cf = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);
        Connection connection = cf.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(TOPIC_NAME);
        MessageProducer producer = session.createProducer(topic);

        TextMessage msg = session.createTextMessage(getCompressibleMessage());

        /*
         * Note: that SOLACE_JMS_PROP_ISXML needs to be false for payload compression to occur. This is only needed for TextMessage.
         * No other message type need this property enabled.
         *
         * Alternatively, in the jndi connection factory on the broker set "Publisher Text Messages as XML Payload" to false (Web interface).
         * This will have the same behaviour.
         */
        msg.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_ISXML, false);

        producer.send(msg);
    }

    private static String getCompressibleMessage() {
        char[] msg = new char[100];
        Arrays.fill(msg, 'A');
        return String.valueOf(msg);
    }
}
