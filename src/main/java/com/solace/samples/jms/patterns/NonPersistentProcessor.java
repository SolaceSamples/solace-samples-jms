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
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * A Processor is a microservice or application that receives a message, does something with the info,
 * and then sends it on..!  It is both a publisher and a subscriber, but (mainly) publishes data once
 * it has received an input message.
 * This class is meant to be used with DirectPub and DirectSub, intercepting the published messages and
 * sending them on to a different topic.
 */
public class NonPersistentProcessor {

    private static final String SAMPLE_NAME = NonPersistentProcessor.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JMS";
    
    private static volatile boolean isShutdown = false;  // are we done yet?

    /** Main method. */
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
        connectionFactory.setDirectTransport(false);    // use Direct transport for "non-persistent" messages
        connectionFactory.setXmlPayload(false);         // use the normal payload section for TextMessage
        connectionFactory.setClientID(API+"_"+SAMPLE_NAME);  // change the name, easier to find
        Connection connection = connectionFactory.createConnection();

        // can be called for ACL violations, connection loss
        connection.setExceptionListener(jmsException -> {  // ExceptionListener.onException()
            System.out.println("### Connection ExceptionListener caught this: "+jmsException);
            if (jmsException.getMessage().contains("JCSMPTransportException")) {
                isShutdown = true;  // bail out
            }
        });

        Session session = connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);  // ACK mode doesn't matter for Direct only

        MessageProducer producer = session.createProducer(null);  // do not bind the producer to a specific topic
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);    // use non-persistent (Direct here) as default
        producer.setDisableMessageID(true);                       // don't auto-populate the JMSMessageID
        producer.setDisableMessageTimestamp(true);                // don't set a send timestamp by default

        
        // Create the subscription topic programmatically, & the message consumer for the subscription topic
        MessageConsumer consumer = session.createConsumer(session.createTopic(TOPIC_PREFIX + "*/direct/pub/>"));
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message inboundMsg) {
                try {
                    // do not print anything to console... too slow!
                    String inboundTopic = inboundMsg.getJMSDestination().toString();
                    // looking for topic "solace/samples/*/direct/pub/>"
                    if (inboundTopic.matches(TOPIC_PREFIX + ".+?/direct/pub/.*")) {  // use of regex to match variable API level
                        // how to "process" the incoming message? maybe do a DB lookup? add some additional properties? or change the payload?
                        TextMessage outboundMsg = session.createTextMessage();
                        final String upperCaseMessage = inboundTopic.toUpperCase();  // as a silly example of "processing"
                        outboundMsg.setText(upperCaseMessage);
                        if (inboundMsg.getJMSMessageID() != null) {
                            outboundMsg.setJMSMessageID(inboundMsg.getJMSMessageID());  // populate for traceability
                        }
                        String [] inboundTopicLevels = inboundTopic.split("/",6);
                        String outboundTopic = new StringBuilder(TOPIC_PREFIX).append(API.toLowerCase())
                                .append("/direct/upper/").append(inboundTopicLevels[5]).toString();
                        try {
                            producer.send(session.createTopic(outboundTopic),outboundMsg);
                        } catch (JMSException e) {
                            System.out.println("### Caught at producer.send() " + e);
                        }
                    }
                } catch (JMSException e) {
                    System.out.println("### Caught in onMessage() " + e);
                }
            }
        });
        
        // just an example of using Solace messages for command-and-control:
        MessageConsumer messageConsumer2 = session.createConsumer(session.createTopic(TOPIC_PREFIX + "control/>"));
        messageConsumer2.setMessageListener(message -> {  // lambda, MessageListener.onMessage(message)
            try {
                if (((Topic)message.getJMSDestination()).getTopicName().endsWith("control/quit")) {
                    System.out.println(">>> QUIT message received, shutting down.");  // example of command-and-control w/msgs
                    isShutdown = true;
                }
            } catch (JMSException e) {
            }
        });
        
        connection.start();  // start receiving messages

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {  // time to loop!
            try {
                Thread.sleep(1000);  // take a pause
            } catch (InterruptedException e) {
                // Thread.sleep() interrupted... probably getting shut down
            }
        }
        isShutdown = true;
        connection.stop();
        connection.close();
        System.out.println("Main thread quitting.");
    }
}
