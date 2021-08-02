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

package com.solace.samples.jms.patterns;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.message.SolMessage;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

/** This is a more detailed subscriber sample. */
public class NonPersistentSubscriber {

    private static final String SAMPLE_NAME = NonPersistentSubscriber.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JMS";
    
    private static volatile int msgRecvCounter = 0;              // num messages received
    private static volatile boolean hasDetectedDiscard = false;  // detected any discards yet?
    private static volatile boolean isShutdown = false;          // are we done yet?

    /** the main method. 
     * @throws Exception */
    public static void main(String... args) throws Exception {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API + " " + SAMPLE_NAME + " initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(args[0]);          // host:port
        connectionFactory.setVPN(args[1]);           // message-vpn
        connectionFactory.setUsername(args[2]);      // client-username
        if (args.length > 3) {
            connectionFactory.setPassword(args[3]);  // client-password
        }
        connectionFactory.setReconnectRetries(2);       // recommended settings
        connectionFactory.setConnectRetriesPerHost(2);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        connectionFactory.setDirectTransport(false);    // use Guaranteed transport for "non-persistent" messages
        connectionFactory.setClientID(API+"_"+SAMPLE_NAME);  // change the name, easier to find
        Connection connection = connectionFactory.createConnection();

        connection.setExceptionListener(jmsException -> {  // ExceptionListener.onException()
            System.out.println("### Connection ExceptionListener caught this: "+jmsException);
            if (jmsException.getMessage().contains("JCSMPTransportException")) {
                isShutdown = true;  // bail out
            }
        });
        
        Session session = connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);  // ACK mode doesn't matter for Direct only

        // Create the subscription topic programmatically, & the message consumer for the subscription topic
        MessageConsumer consumer = session.createConsumer(session.createTopic(TOPIC_PREFIX + "*/direct/>"));
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                // do not print anything to console... too slow!
                msgRecvCounter++;
                if (((SolMessage)message).getMessage().getDiscardIndication()) {
                    // since Direct messages, check if there have been any lost any messages
                    // If the consumer is being over-driven (i.e. publish rates too high), the broker might discard some messages for this consumer
                    // check this flag to know if that's happened
                    // to avoid discards:
                    //  a) reduce publish rate
                    //  b) use multiple-threads or shared subscriptions for parallel processing
                    //  c) increase size of consumer's D-1 egress buffers (check client-profile) (helps more with bursts)
                    hasDetectedDiscard = true;  // set my own flag
                }
            }
        });
        
        // just an example of using Solace messages for command-and-control:
        MessageConsumer messageConsumer2 = session.createConsumer(session.createTopic(TOPIC_PREFIX + "control/>"));
        messageConsumer2.setMessageListener(message -> {  // lambda, MessageListener.onMessage(message)
            try {
                if (message.getJMSDestination().toString().endsWith("control/quit")) {
                    System.out.println(">>> QUIT message received, shutting down.");  // example of command-and-control w/msgs
                    isShutdown = true;
                }
            } catch (JMSException e) {
            }
        });

        connection.start();

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        try {
            while (System.in.available() == 0 && !isShutdown) {
                Thread.sleep(1000);  // wait 1 second
                System.out.printf("%s Received msgs/s: %,d%n",API,msgRecvCounter);  // simple way of calculating message rates
                msgRecvCounter = 0;
                if (hasDetectedDiscard) {
                    System.out.println("*** Egress discard detected *** : "
                            + SAMPLE_NAME + " unable to keep up with full message rate");
                    hasDetectedDiscard = false;  // only show the error once per second
                }
            }
        } catch (InterruptedException e) {
            // Thread.sleep() interrupted... probably getting shut down
        }
        System.out.println("********** We are outside the loop");
        isShutdown = true;
        connection.stop();
        System.out.println("********** after connection stop");
//        session.close();
        connection.close();  // could block here for a while.
        System.out.println("Main thread quitting.");
    }
}
