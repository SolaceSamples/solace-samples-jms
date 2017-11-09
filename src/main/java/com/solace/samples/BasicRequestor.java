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
 *  Solace JMS 1.1 Examples: BasicRequestor
 */

package com.solace.samples;

import java.util.UUID;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

/**
 * Sends a request message using Solace JMS API implementation and receives a reply to it.
 * 
 * This is the Requestor in the Request/Reply messaging pattern.
 */
public class BasicRequestor {

    final String REQUEST_TOPIC_NAME = "T/GettingStarted/requests";

    final int REPLY_TIMEOUT_MS = 10000; // 10 seconds

    public void run(String... args) throws Exception {

        String[] split = args[1].split("@");

        String host = args[0];
        String vpnName = split[1];
        String username = split[0];
        String password = args[2];

        System.out.printf("BasicRequestor is connecting to Solace messaging at %s...%n", host);

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setVPN(vpnName);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        // Create connection to the Solace router
        Connection connection = connectionFactory.createConnection();

        // Create a non-transacted, auto ACK session.
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        System.out.printf("Connected to the Solace Message VPN '%s' with client username '%s'.%n", vpnName,
                username);

        // Create the request topic programmatically
        Topic requestTopic = session.createTopic(REQUEST_TOPIC_NAME);

        // Create the message producer for the created queue
        MessageProducer requestProducer = session.createProducer(requestTopic);

        // The response will be received on this temporary queue.
        TemporaryQueue replyToQueue = session.createTemporaryQueue();

        // Create consumer for receiving the request's reply
        MessageConsumer replyConsumer = session.createConsumer(replyToQueue);

        // Start receiving replies
        connection.start();

        // Create a request.
        TextMessage request = session.createTextMessage("Sample Request");
        // The application must put the destination of the reply in the replyTo field of the request
        request.setJMSReplyTo(replyToQueue);
        // The application must put a correlation ID in the request
        String correlationId = UUID.randomUUID().toString();
        request.setJMSCorrelationID(correlationId);

        System.out.printf("Sending request '%s' to topic '%s'...%n", request.getText(), requestTopic.toString());

        // Send the request
        requestProducer.send(requestTopic, request, DeliveryMode.NON_PERSISTENT,
                Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);

        System.out.println("Sent successfully. Waiting for reply...");

        // the main thread blocks at the next statement until a message received or the timeout occurs
        Message reply = replyConsumer.receive(REPLY_TIMEOUT_MS);

        if (reply == null) {
            throw new Exception("Failed to receive a reply in " + REPLY_TIMEOUT_MS + " msecs");
        }

        // Process the reply
        if (reply.getJMSCorrelationID() == null) {
            throw new Exception(
                    "Received a reply message with no correlationID. This field is needed for a direct request.");
        }

        // Apache Qpid JMS prefixes correlation ID with string "ID:" so remove such prefix for interoperability
        if (!reply.getJMSCorrelationID().replaceAll("ID:", "").equals(correlationId)) {
            throw new Exception("Received invalid correlationID in reply message.");
        }

        if (reply instanceof TextMessage) {
            System.out.printf("TextMessage response received: '%s'%n", ((TextMessage) reply).getText());
            if (!reply.getBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE)) {
                System.out.println("Warning: Received a reply message without the isReplyMsg flag set.");
            }
        } else {
            System.out.println("Message response received.");
        }

        System.out.printf("Message Content:%n%s%n", SolJmsUtility.dumpMessage(reply));

        connection.stop();
        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        replyConsumer.close();
        requestProducer.close();
        session.close();
        connection.close();
    }

    public static void main(String... args) throws Exception {
        if (args.length != 3 || args[1].split("@").length != 2) {
            System.out.println("Usage: BasicRequestor <host:port> <client-username@message-vpn> <client-password>");
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
        new BasicRequestor().run(args);
    }
}
