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
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

public class TopicSubscriber {

    public void run(String... args) throws JMSException, NamingException {
        System.out.println("TopicSubscriber initializing...");

        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String) args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
        env.put(Context.SECURITY_PRINCIPAL, "clientUsername");
        env.put(Context.SECURITY_CREDENTIALS, "password");

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        ConnectionFactory cf = (ConnectionFactory) initialContext.lookup("/JNDI/CF/GettingStarted");
        // JMS Connection
        Connection connection = cf.createConnection();

        // Create a non-transacted, Auto Ack session.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Topic in Solace JNDI.
        final Topic topic = (Topic) initialContext.lookup("/JNDI/T/GettingStarted/pubsub");

        // Latch used for synchronizing b/w threads
        final CountDownLatch latch = new CountDownLatch(1);

        final MessageConsumer consumer = session.createConsumer(topic);

        /** Anonymous inner-class for receiving messages **/
        consumer.setMessageListener(new MessageListener() {

            @Override
            public void onMessage(Message message) {

                try {
                    if (message instanceof TextMessage) {
                        System.out.printf("TextMessage received: '%s'%n", ((TextMessage) message).getText());
                    } else {
                        System.out.println("Message received.");
                    }
                    System.out.printf("Message Dump:%n%s%n", SolJmsUtility.dumpMessage(message));
                    latch.countDown(); // unblock main thread
                } catch (JMSException e) {
                    System.out.println("Error processing incoming message.");
                    e.printStackTrace();
                }

            }
        });

        // Start receiving messages
        connection.start();
        System.out.println("Connected. Awaiting message...");

        try {
            latch.await(); // block here until message received, and latch will
                           // flip
        } catch (InterruptedException e) {
            System.out.println("I was awoken while waiting");
        }

        // Close consumer
        connection.close();
        initialContext.close();
        System.out.println("Exiting.");
    }

    public static void main(String... args) throws JMSException, NamingException {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: TopicSubscriber <msg_backbone_ip:port>");
            System.exit(-1);
        }

        TopicSubscriber app = new TopicSubscriber();
        app.run(args);
    }
}
