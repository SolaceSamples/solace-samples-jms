/**
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

/**
 *  Solace JMS 1.1 Examples: BasicReplier
 */

package com.solace.samples;

import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

/**
 * Receives a request message using Solace JMS API implementation and replies to it.
 * 
 * This is the Replier in the Request/Reply messaging pattern.
 */
public class BasicReplier {

    final String REQUEST_TOPIC_NAME = "T/GettingStarted/requests";

    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(1);

    public void run(String... args) throws Exception {

        String[] split = args[1].split("@");

        String host = args[0];
        String vpnName = split[1];
        String username = split[0];
        String password = args[2];

        System.out.printf("BasicReplier is connecting to Solace messaging at %s...%n", host);

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setVPN(vpnName);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        // Create connection to Solace messaging
        Connection connection = connectionFactory.createConnection();

        // Create a non-transacted, auto ACK session.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        System.out.printf("Connected to the Solace Message VPN '%s' with client username '%s'.%n", vpnName,
                username);

        // Create the request topic programmatically
        Topic requestTopic = session.createTopic(REQUEST_TOPIC_NAME);

        // Create the message consumer for the request topic
        MessageConsumer requestConsumer = session.createConsumer(requestTopic);

        // Create the message producer for the reply queue
        final MessageProducer replyProducer = session.createProducer(null);

        // Use the anonymous inner class for receiving request messages asynchronously
        requestConsumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message request) {
                try {
                    Destination replyDestination = request.getJMSReplyTo();
                    if (replyDestination != null) {
                        System.out.println("Received request, responding...");

                        TextMessage reply = session.createTextMessage();
                        String text = "Sample response";
                        reply.setText(text);

                        // Copy the correlation ID from the request to the reply
                        reply.setJMSCorrelationID(request.getJMSCorrelationID());

                        // For direct messaging only, this flag is needed to interoperate with
                        // Solace Java, C, and C# request reply APIs.
                        reply.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE, Boolean.TRUE);

                        // Sent the reply
                        replyProducer.send(replyDestination, reply, DeliveryMode.NON_PERSISTENT,
                                Message.DEFAULT_PRIORITY,
                                Message.DEFAULT_TIME_TO_LIVE);
                        System.out.println("Responded successfully. Exiting...");

                        latch.countDown(); // unblock the main thread
                    } else {
                        System.out.println("Received message without reply-to field.");
                    }
                } catch (JMSException ex) {
                    System.out.println("Error processing incoming message.");
                    ex.printStackTrace();
                }
            }
        });

        // Start receiving messages
        connection.start();
        System.out.println("Awaiting request...");
        // the main thread blocks at the next statement until a message received
        latch.await();

        connection.stop();
        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        replyProducer.close();
        requestConsumer.close();
        session.close();
        connection.close();
    }

    public static void main(String... args) throws Exception {
        if (args.length != 3 || args[1].split("@").length != 2) {
            System.out.println("Usage: TopicPublisher <host:port> <client-username@message-vpn> <client-password>");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[0].isEmpty()) {
            System.out.println("No client-username entered");
            System.out.println();
            System.exit(-1);
        }
        if (args[1].split("@")[1].isEmpty()) {
            System.out.println("No message-vpn entered");
            System.out.println();
            System.exit(-1);
        }
        new BasicReplier().run(args);
    }
}
