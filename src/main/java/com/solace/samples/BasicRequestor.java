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

package com.solace.samples;

import java.util.Hashtable;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

public class BasicRequestor {

    public void run(String... args) throws Exception {

        System.out.println("BasicReplier initializing...");

        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String) args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
        env.put(Context.SECURITY_PRINCIPAL, "clientUsername");

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        ConnectionFactory cf = (ConnectionFactory) initialContext.lookup("/JNDI/CF/GettingStarted");
        // JMS Connection
        Connection connection = cf.createConnection();

        // Create a non-transacted, Auto Ack session.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Topic in Solace JNDI.
        final Topic requestDestination = (Topic) initialContext.lookup("/JNDI/T/GettingStarted/requests");

        final MessageProducer producer = session.createProducer(requestDestination);

        // The response will be received on this temporary queue.
        TemporaryQueue replyToQueue = session.createTemporaryQueue();
        // From the session, create a consumer for receiving the request's
        // reply from RRDirectReplier
        final MessageConsumer consumer = session.createConsumer(replyToQueue);
        connection.start();

        // Time to wait for a reply before timing out
        final int timeoutMs = 10000;
        TextMessage request = session.createTextMessage();
        final String text = "Sample Request";
        request.setText(text);

        // The application must put the destination of the reply in the replyTo
        // field of the request
        request.setJMSReplyTo(replyToQueue);
        // The application must put a correlation ID in the request
        String correlationId = UUID.randomUUID().toString();
        request.setJMSCorrelationID(correlationId);

        System.out.printf("Connected. About to send request message '%s' to topic '%s'...%n", text,
                requestDestination.toString());

        // Send the request then wait for a reply
        producer.send(requestDestination, request, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);
        Message reply = consumer.receive(timeoutMs);

        if (reply == null) {
            System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
            return;
        }

        if (reply.getJMSCorrelationID() == null) {
            throw new Exception(
                    "Received a reply message with no correlationID.  This field is needed for a direct request.");
        }

        // The reply's correlationID should match the request's correlationID
        if (!reply.getJMSCorrelationID().equals(correlationId)) {
            throw new Exception("Received invalid correlationID in reply message.");
        }

        // Process the reply
        if (reply instanceof TextMessage) {
            System.out.printf("TextMessage response received: '%s'%n", ((TextMessage) reply).getText());
            if (!reply.getBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE)) {
                System.out.println("Warning: Received a reply message without the isReplyMsg flag set.");
            }

        }

        System.out.printf("Response Message Dump:%n%s%n", SolJmsUtility.dumpMessage(reply));

        // Close consumer
        connection.close();
        initialContext.close();
        System.out.println("Exiting.");
    }

    public static void main(String... args) throws Exception {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: BasicRequestor <msg_backbone_ip:port>");
            System.exit(-1);
        }

        BasicRequestor app = new BasicRequestor();
        app.run(args);
    }
}
