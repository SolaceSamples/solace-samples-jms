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

import java.io.IOException;

/**
 * A Processor is a microservice or application that receives a message, does something with the info,
 * and then sends it on..!  It is both a publisher and a subscriber, but (mainly) publishes data once
 * it has received an input message.
 * This class is meant to be used with DirectPub and DirectSub, intercepting the published messages and
 * sending them on to a different topic.
 */
public class DirectProcessor {

    private static final String SAMPLE_NAME = DirectProcessor.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    
    private static volatile boolean isShutdown = false;  // are we done yet?

    /** Main method. */
    public static void main(String... args) throws JCSMPException, IOException {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(SAMPLE_NAME + " initializing...");

        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, args[0]);          // host:port
        properties.setProperty(JCSMPProperties.VPN_NAME,  args[1]);     // message-vpn
        properties.setProperty(JCSMPProperties.USERNAME, args[2]);      // client-username
        if (args.length > 3) {
            properties.setProperty(JCSMPProperties.PASSWORD, args[3]);  // client-password
        }
        properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);  // re-subscribe Direct subs after reconnect
        JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
        channelProps.setReconnectRetries(20);      // recommended settings
        channelProps.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        properties.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);
        final JCSMPSession session;
        session = JCSMPFactory.onlyInstance().createSession(properties, null, new SessionEventHandler() {
            @Override
            public void handleEvent(SessionEventArgs event) {  // could be reconnecting, connection lost, etc.
                System.out.printf("### Received a Session event: %s%n", event);
            }
        });
        session.connect();  // connect to the broker

        // Simple anonymous inner-class for handling publishing events
        final XMLMessageProducer producer;
        producer = session.getMessageProducer(new JCSMPStreamingPublishCorrelatingEventHandler() {
            // unused in Direct Messaging application, only for Guaranteed/Persistent publishing application
            @Override public void responseReceivedEx(Object key) {
            }

            // can be called for ACL violations, connection loss, and Persistent NACKs
            @Override
            public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
                System.out.printf("### Producer handleErrorEx() callback: %s%n", cause);
                if (cause instanceof JCSMPTransportException) {  // unrecoverable, all reconnect attempts failed
                    isShutdown = true;
                } else if (cause instanceof JCSMPErrorResponseException) {  // might have some extra info
                    JCSMPErrorResponseException e = (JCSMPErrorResponseException)cause;
                    System.out.println(JCSMPErrorResponseSubcodeEx.getSubcodeAsString(e.getSubcodeEx())
                            + ": " + e.getResponsePhrase());
                    System.out.println(cause);
                }
            }
        });

        // Simple anonymous inner-class for request handling
        final XMLMessageConsumer cons = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage inboundMsg) {
                String inboundTopic = inboundMsg.getDestination().getName();
                if (inboundTopic.matches(TOPIC_PREFIX + ".+?/direct/pub/.*")) {  // use of regex to match variable API level
                    // how to "process" the incoming message? maybe do a DB lookup? add some additional properties? or change the payload?
                    TextMessage outboundMsg = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
                    final String upperCaseMessage = inboundTopic.toUpperCase();  // as a silly example of "processing"
                    outboundMsg.setText(upperCaseMessage);
                    if (inboundMsg.getApplicationMessageId() != null) {
                        outboundMsg.setApplicationMessageId(inboundMsg.getApplicationMessageId());  // populate for traceability
                    }
                    String [] inboundTopicLevels = inboundTopic.split("/",6);
                    String outboundTopic = new StringBuilder(TOPIC_PREFIX).append("jcsmp/direct/upper/").append(inboundTopicLevels[5]).toString();
                    try {
                        producer.send(outboundMsg, JCSMPFactory.onlyInstance().createTopic(outboundTopic));
                    } catch (JCSMPException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                        System.out.printf("### Caught while trying to producer.send(): %s%n",e);
                        if (e instanceof JCSMPTransportException) {  // unrecoverable
                            isShutdown = true;
                        }
                    }
                } else if (inboundMsg.getDestination().getName().endsWith("control/quit")) {  // special sample message
                    System.out.println(">>> QUIT message received, shutting down.");  // example of command-and-control w/msgs
                    isShutdown = true;
                }
            }

            public void onException(JCSMPException e) {
                System.out.printf("Consumer received exception: %s%n", e);
            }
        });

        session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_PREFIX + "*/direct/pub/>"));  // listen to
        session.addSubscription(JCSMPFactory.onlyInstance().createTopic(TOPIC_PREFIX + "control/>"));
        cons.start();

        System.out.println(SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        while (System.in.available() == 0 && !isShutdown) {  // time to loop!
            try {
                Thread.sleep(1000);  // take a pause
            } catch (InterruptedException e) {
                // Thread.sleep() interrupted... probably getting shut down
            }
        }
        isShutdown = true;
        session.closeSession();  // will also close producer and consumer objects
        System.out.println("Main thread quitting.");
    }
}
