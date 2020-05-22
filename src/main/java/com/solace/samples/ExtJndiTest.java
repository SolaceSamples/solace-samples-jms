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
 *  Solace JMS 1.1 Examples: ExtJndiTest
 */

package com.solace.samples;

import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SupportedProperty;

/**
 * Sends a persistent message to a queue using external JNDI lookup, then reads it back.
 * 
 * The queue used for messages must exist on the message broker.
 */
public class ExtJndiTest {    

    // JNDI Initial Context Factory
    private static final String JNDI_INITIAL_CONTEXT_FACTORY = 
            // "com.ibm.websphere.naming.WsnInitialContextFactory";
            // "com.sun.jndi.ldap.LdapCtxFactory";
            "com.sun.jndi.fscontext.RefFSContextFactory";
            // "com.sun.jndi.rmi.registry.RegistryContextFactory";

    // The URL to the JNDI server
    private String jndiUrl;
    
    // Username used to log into the JNDI server
    private String jndiUsername;

    // Password used to log into the JNDI server
    private String jndiPassword;

    // Connection Factory Distinguished Name - Must exist in the JNDI
    private String cfName;
    
    // Destination Distinguished Name  - Must exist in the JNDI
    private String destinationName;
        
    // Latch used for synchronizing between threads
    final CountDownLatch latch = new CountDownLatch(1);

    private void printUsage() {
        System.out.println("\nUsage: \nSExtJndiTest" + 
                " -jndiUrl URL -jndiUsername USERNAME -jndiPassword PASSWORD -cf CONNECTION_FACTORY_DN -destination DESTINATION_DN\n");
    }
        
    private void run() {   
        
        // Initial Context
        InitialContext ctx = null;

        // JMS Connection
        Connection connection = null;
            
        try {
            // Create the LDAP Initial Context
            Hashtable<String,String> env = new Hashtable<String,String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_INITIAL_CONTEXT_FACTORY);
            env.put(Context.PROVIDER_URL, jndiUrl);
            env.put(Context.REFERRAL, "throw");
            env.put(Context.SECURITY_PRINCIPAL, jndiUsername);
            env.put(Context.SECURITY_CREDENTIALS, jndiPassword);
            ctx = new InitialContext(env);

            // lookup the connection factory
            SolConnectionFactory cf = (SolConnectionFactory)ctx.lookup(cfName);
            
            // lookup the destination
            Object destination = ctx.lookup(destinationName);
                            
            // Create a JMS Connection instance .
            connection = cf.createConnection();
                                    
            // Create a non-transacted, client ACK session.
            Session session = connection.createSession(false, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);

            // Create the producer and consumer
            MessageConsumer consumer = null;
            MessageProducer producer = null;
            if (destination instanceof Topic) {
                Topic topic = (Topic)destination;
                consumer = session.createConsumer(topic);
                producer = session.createProducer(topic);
            } else if (destination instanceof Queue) {
                Queue queue = (Queue)destination;
                consumer = session.createConsumer(queue);
                producer = session.createProducer(queue);
            } else {
                System.out.println("Destination in JNDI must be a topic or a queue");
                System.exit(0);
            }
            
            // set the consumer's message listener
            consumer.setMessageListener(new MessageListener() {
                public void onMessage(Message message) {
                    if (message instanceof TextMessage) {
                        try {
                            System.out.println("*** Received Message with content: " + ((TextMessage)message).getText());
                            // ACK the received message because of the set SupportedProperty.SOL_CLIENT_ACKNOWLEDGE above
                            message.acknowledge();
                            latch.countDown(); // unblock the main thread
                            } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Received Message: " + message);
                    }
                }
            });
            
            // Start the JMS Connection.
            connection.start();

            // Send a message to the consumer
            Message testMessage = session.createTextMessage("SolJMSJNDITest message");
            producer.send(testMessage);
            System.out.println("*** Sent Message with content: " + ((TextMessage)testMessage).getText());
            
            // Block main thread and wait for the message to be received and printed out before exiting
            latch.await();
            connection.stop();
            consumer.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {}
            }
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {}
            }
        }
    }
        
    public static void main(String[] args) {
        try {
            ExtJndiTest instance = new ExtJndiTest();
             for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-jndiUrl")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.jndiUrl = args[i];
                } else if (args[i].equals("-jndiUsername")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.jndiUsername = args[i];
                } else if (args[i].equals("-jndiPassword")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.jndiPassword = args[i];      
                } else if (args[i].equals("-cf")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.cfName = args[i];      
                } else if (args[i].equals("-destination")) {
                    i++;
                    if (i >= args.length) instance.printUsage();
                    instance.destinationName = args[i];      
                } else {
                    instance.printUsage();
                    System.out.println("Illegal argument specified - " + args[i]);
                    return;
                }
            }

            if (instance.jndiUrl == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiUrl\" parameter");
                return;
            }
            if (instance.jndiUsername == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiUsername\" parameter");
                return;
            }
            if (instance.jndiPassword == null) {
                instance.printUsage();
                System.out.println("Please specify \"-jndiPassword\" parameter");
                return;
            }
            if (instance.cfName == null) {
                instance.printUsage();
                System.out.println("Please specify \"-cf\" parameter");
                return;
            }
            if (instance.destinationName == null) {
                instance.printUsage();
                System.out.println("Please specify \"-destination\" parameter");
                return;
            }

            instance.run();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }    
}
