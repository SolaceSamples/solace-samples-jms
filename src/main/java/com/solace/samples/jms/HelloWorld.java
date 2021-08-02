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

package com.solace.samples.jms;

import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;
import com.solacesystems.jms.message.SolMessage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * This simple introductory sample shows an application that both publishes and subscribes.
 */
public class HelloWorld {
    
    private static final String SAMPLE_NAME = HelloWorld.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JMS";
    private static volatile boolean isShutdown = false;           // are we done yet?

    /** Simple application for doing pubsub. */
    public static void main(String... args) throws Exception {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        // User prompt, what is your name??, to use in the topic
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String uniqueName = "";
        while (uniqueName.isEmpty()) {
            System.out.printf("Hello! Enter your name, or a unique word: ");
            uniqueName = reader.readLine().trim().replaceAll("\\s+", "_");  // clean up whitespace
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
        connectionFactory.setDirectTransport(false);     // use Guaranteed transport for "non-persistent" messages
        connectionFactory.setXmlPayload(false);  // use the normal payload section for TextMessage
        connectionFactory.setClientID(API+"_"+SAMPLE_NAME);  // change the name, easier to find
        Connection connection = connectionFactory.createConnection();
        
        Session session = connection.createSession(false,Session.CLIENT_ACKNOWLEDGE);  // ACK mode doesn't matter for Direct only

        // Create the subscription topic programmatically, & the message consumer for the subscription topic
        MessageConsumer consumer = session.createConsumer(session.createTopic(TOPIC_PREFIX + "*/hello/>"));
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                // could be 4 different message types: Text, Map, Stream, and just plain binary (BytesMessage)
                System.out.printf("vvv RECEIVED A MESSAGE vvv%n%s===%n",((SolMessage)message).dump());  // just print
            }
        });

        connection.start();  // start receiving messages

        MessageProducer producer = session.createProducer(null);  // do not bind the producer to a specific topic
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);    // use non-persistent (Direct here) as default

        System.out.printf("%nConnected and subscribed. Ready to publish. Press [ENTER] to quit.%n");
        System.out.printf(" ~ Run this sample twice splitscreen to see true publish-subscribe. ~%n%n");

        TextMessage message = session.createTextMessage();
        while (System.in.available() == 0 && !isShutdown) {  // loop now, just use main thread
            try {
                Thread.sleep(5000);  // take a pause
                // specify a text payload
                message.setText(String.format("Hello World from %s!",uniqueName));
                // make a dynamic topic: solace/samples/jms/hello/[uniqueName]
                String topicString = TOPIC_PREFIX + API.toLowerCase() + "/hello/" + uniqueName.toLowerCase();
                System.out.printf(">> Calling send() on '%s'%n",topicString);
                producer.send(session.createTopic(topicString), message);
                message.clearBody();     // reuse this message on the next loop, to avoid having to recreate it
            } catch (JMSException e) {
                System.out.printf("### Exception caught during producer.send(): %s%n",e);
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
