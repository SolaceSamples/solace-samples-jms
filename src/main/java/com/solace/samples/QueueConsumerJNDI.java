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
 *  Solace JMS 1.1 Examples: QueueConsumerJNDI
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
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolJmsUtility;

/**
 * Receives a persistent message from a queue using Solace JMS API implementation. Connection to the Solace message
 * router is setup using JNDI.
 *
 * The queue used for messages must exist on the message broker.
 */
public class QueueConsumerJNDI {

    final String SOLACE_VPN = "default";
    final String SOLACE_USERNAME = "clientUsername";
    final String SOLACE_PASSWORD = "password";

    final String QUEUE_NAME = "Q/tutorial";
    final String QUEUE_JNDI_NAME = "/JNDI/" + QUEUE_NAME;
    final String CONNECTION_FACTORY_JNDI_NAME = "/JNDI/CF/GettingStarted";

    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(1);

    public void run(String... args) throws Exception {
        String solaceHost = args[0];
        System.out.printf("QueueConsumerJNDI is connecting to Solace router %s...%n", solaceHost);

        // setup environment variables for creating of the initial context
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        // use the Solace JNDI initial context factory
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
  
        // assign Solace message router connection parameters
        env.put(InitialContext.PROVIDER_URL, solaceHost);
        env.put(Context.SECURITY_PRINCIPAL, SOLACE_USERNAME + '@' + SOLACE_VPN); // Formatted as user@message-vpn
        env.put(Context.SECURITY_CREDENTIALS, SOLACE_PASSWORD);

        // Create the initial context that will be used to lookup the JMS Administered Objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup the connection factory
        ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

        // Create connection to the Solace router
        Connection connection = connectionFactory.createConnection();

        // Create a non-transacted, client ACK session.
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        System.out.printf("Connected to the Solace Message VPN '%s' with client username '%s'.%n", SOLACE_VPN,
                SOLACE_USERNAME);

        // Lookup the queue.
        Queue queue = (Queue) initialContext.lookup(QUEUE_JNDI_NAME);

        // From the session, create a consumer for the destination.
        MessageConsumer messageConsumer = session.createConsumer(queue);

        // Use the anonymous inner class for receiving messages asynchronously
        messageConsumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    if (message instanceof TextMessage) {
                        System.out.printf("TextMessage received: '%s'%n", ((TextMessage) message).getText());
                    } else {
                        System.out.println("Message received.");
                    }
                    System.out.printf("Message Content:%n%s%n", SolJmsUtility.dumpMessage(message));

                    // ACK the received message manually because of the set Session.CLIENT_ACKNOWLEDGE above
                    message.acknowledge();

                    latch.countDown(); // unblock the main thread
                } catch (JMSException ex) {
                    System.out.println("Error processing incoming message.");
                    ex.printStackTrace();
                }
            }
        });

        // Start receiving messages
        connection.start();
        System.out.println("Awaiting message...");
        // the main thread blocks at the next statement until a message received
        latch.await();

        connection.stop();
        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        messageConsumer.close();
        session.close();
        connection.close();
        // The initial context needs to be close; it does not extend AutoCloseable
        initialContext.close();
    }

    public static void main(String... args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: QueueConsumerJNDI <msg_backbone_ip:port>");
            System.exit(-1);
        }
        new QueueConsumerJNDI().run(args);
    }
}
