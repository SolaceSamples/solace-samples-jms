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
 *  Solace JMS 1.1 Examples: QueueProducerJNDI
 */

package com.solace.samples;

import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

/**
 * Sends a persistent message to a queue using Solace JMS API implementation.
 * 
 * The queue used for messages must exist on the message broker.
 */
public class QueueProducerJNDI {

    final String QUEUE_NAME = "Q/tutorial";
    final String QUEUE_JNDI_NAME = "/JNDI/" + QUEUE_NAME;
    final String CONNECTION_FACTORY_JNDI_NAME = "/JNDI/CF/GettingStarted";

    public void run(String... args) throws Exception {

        String[] split = args[1].split("@");

        String host = args[0];
        String vpnName = split[1];
        String username = split[0];
        String password = args[2];

        System.out.printf("QueueProducerJNDI is connecting to Solace messaging at %s...%n", host);

        // setup environment variables for creating of the initial context
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        // use the Solace JNDI initial context factory
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");

        // assign Solace messaging connection parameters
        env.put(InitialContext.PROVIDER_URL, host);
        env.put(Context.SECURITY_PRINCIPAL, username + '@' + vpnName); // Formatted as user@message-vpn
        env.put(Context.SECURITY_CREDENTIALS, password);

        // Create the initial context that will be used to lookup the JMS Administered Objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup the connection factory
        ConnectionFactory connectionFactory = (ConnectionFactory) initialContext.lookup(CONNECTION_FACTORY_JNDI_NAME);

        // Create connection to Solace messaging
        Connection connection = connectionFactory.createConnection();

        // Create a non-transacted, auto ACK session.
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        System.out.printf("Connected to the Solace Message VPN '%s' with client username '%s'.%n", vpnName,
                username);

        // Lookup the queue.
        Queue queue = (Queue) initialContext.lookup(QUEUE_JNDI_NAME);

        // Create the message producer for the created queue
        MessageProducer messageProducer = session.createProducer(queue);

        // Create a text message.
        TextMessage message = session.createTextMessage("Hello world Queues!");

        System.out.printf("Sending message '%s' to queue '%s'...%n", message.getText(), queue.toString());

        // Send the message
        // NOTE: JMS Message Priority is not supported by the Solace Message Bus
        messageProducer.send(queue, message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);

        System.out.println("Sent successfully. Exiting...");

        // Close everything in the order reversed from the opening order
        // NOTE: as the interfaces below extend AutoCloseable,
        // with them it's possible to use the "try-with-resources" Java statement
        // see details at https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
        messageProducer.close();
        session.close();
        connection.close();
        // The initial context needs to be close; it does not extend AutoCloseable
        initialContext.close();
    }

    public static void main(String... args) throws Exception {
        if (args.length != 3 || args[1].split("@").length != 2) {
            System.out.println("Usage: QueueProducerJNDI <host:port> <client-username@message-vpn> <client-password>");
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
        new QueueProducerJNDI().run(args);
    }
}
