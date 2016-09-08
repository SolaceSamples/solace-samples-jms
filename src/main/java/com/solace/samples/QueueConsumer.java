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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.SupportedProperty;

public class QueueConsumer {

    public void run(String... args) throws Exception {

        System.out.println("QueueConsumer initializing...");

        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String) args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
        env.put(Context.SECURITY_PRINCIPAL, "clientUsername");

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        QueueConnectionFactory cf = (QueueConnectionFactory) initialContext.lookup("/JNDI/CF/GettingStarted");
        // JMS Connection
        QueueConnection connection = cf.createQueueConnection();

        // Create a non-transacted, Client Ack session.
        Session session = connection.createQueueSession(false, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);

        // Lookup Queue.
        Queue queue = (Queue) initialContext.lookup("/JNDI/Q/tutorial");

        // Latch used for synchronizing b/w threads
        final CountDownLatch latch = new CountDownLatch(1);

        // From the session, create a consumer for the destination.
        MessageConsumer consumer = session.createConsumer(queue);

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

                    message.acknowledge();

                    latch.countDown(); // unblock main thread
                } catch (JMSException e) {
                    System.out.println("Error processing incoming message.");
                    e.printStackTrace();
                }

            }
        });

        // Do not forget to start the JMS Connection.
        connection.start();

        // Output a message on the console.
        System.out.println("Waiting for a message ... (press Ctrl+C) to terminate ");

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

    public static void main(String... args) throws Exception {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: QueueConsumer <msg_backbone_ip:port>");
            System.exit(-1);
        }

        QueueConsumer app = new QueueConsumer();
        app.run(args);
    }
}
