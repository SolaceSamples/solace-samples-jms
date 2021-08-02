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
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * A more performant sample that shows an application that publishes.
 */
public class NonPersistentPublisher {
    
    private static final String SAMPLE_NAME = NonPersistentPublisher.class.getSimpleName();
    private static final String TOPIC_PREFIX = "solace/samples/";  // used as the topic "root"
    private static final String API = "JMS";
    private static final int APPROX_MSG_RATE_PER_SEC = 100;
    private static final int PAYLOAD_SIZE = 100;
    
    private static volatile int msgSentCounter = 0;                   // num messages sent
    private static volatile boolean isShutdown = false;

    /** Main method. */
    public static void main(String... args) throws Exception {
        if (args.length < 3) {  // Check command line arguments
            System.out.printf("Usage: %s <host:port> <message-vpn> <client-username> [password]%n%n", SAMPLE_NAME);
            System.exit(-1);
        }
        System.out.println(API.toUpperCase()+ " " + SAMPLE_NAME + " initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
        connectionFactory.setHost(args[0]);          // host:port
        connectionFactory.setVPN(args[1]);           // message-vpn
        connectionFactory.setUsername(args[2]);      // client-username
        if (args.length > 3) {
            connectionFactory.setPassword(args[3]);  // client-password
        }
        connectionFactory.setReconnectRetries(20);      // recommended settings
        connectionFactory.setConnectRetriesPerHost(5);  // recommended settings
        // https://docs.solace.com/Solace-PubSub-Messaging-APIs/API-Developer-Guide/Configuring-Connection-T.htm
        connectionFactory.setDirectTransport(false);    // use Guaranteed transport for "non-persistent" messages
        connectionFactory.setXmlPayload(false);         // use the normal payload section for TextMessage
        connectionFactory.setClientID(API+"_"+SAMPLE_NAME);  // change the name, easier to find
        Connection connection = connectionFactory.createConnection();

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

        // preallocate a binary message, reuse it each loop, for performance
        final BytesMessage message = session.createBytesMessage();
        ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
        publishExecutor.submit(() -> {  // create an application thread for publishing in a loop
            byte[] payload = new byte[PAYLOAD_SIZE];  // preallocate memory, for reuse, for performance
            while (!isShutdown) {
                try {
                    // each loop, change the payload, less trivial
                    char chosenCharacter = (char)(Math.round(msgSentCounter % 26) + 65);  // rotate through letters [A-Z]
                    Arrays.fill(payload,(byte)chosenCharacter);  // fill the payload completely with that char
                    message.writeBytes(payload);
                    message.setJMSMessageID(UUID.randomUUID().toString());  // as an example of a header
                    // dynamic topics!!  "solace/samples/jms/direct/pub/A"
                    String topicString = new StringBuilder(TOPIC_PREFIX)
                            .append(API.toLowerCase()).append("/direct/pub/").append(chosenCharacter).toString();
                    producer.send(session.createTopic(topicString),message);  // send the message
                    msgSentCounter++;  // add one
                    message.clearBody();  // re-use the message
                    Thread.sleep(1000 / APPROX_MSG_RATE_PER_SEC);  // do Thread.sleep(0) for max speed
                    // Note: STANDARD Edition Solace PubSub+ broker is limited to 10k msg/s max ingress
                } catch (JMSException e) {  // threw from send(), only thing that is throwing here, but keep trying (unless shutdown?)
                    System.out.printf("### Caught while trying to producer.send(): %s%n",e);
//                    if (e instanceof JCSMPTransportException) {  // unrecoverable
//                        isShutdown = true;
//                    }
                } catch (InterruptedException e) {
                    isShutdown = true;
                }
            }
            try {  // try to send a QUIT message to the other applications... (as an example of command-and-control)
                message.clearBody();
                producer.send(session.createTopic(TOPIC_PREFIX+"control/quit"),message);
            } catch (JMSException e) {
            }
            publishExecutor.shutdown();
        });

        System.out.println(API + " " + SAMPLE_NAME + " connected, and running. Press [ENTER] to quit.");
        // block the main thread, waiting for a quit signal
        while (System.in.available() == 0 && !isShutdown) {
            try {
                Thread.sleep(1000);
                System.out.printf("%s Published msgs/s: %,d%n",API,msgSentCounter);  // simple way of calculating message rates
                msgSentCounter = 0;
            } catch (InterruptedException e) {
                // Thread.sleep() interrupted... probably getting shut down
            }
        }
        isShutdown = true;
        connection.stop();
        Thread.sleep(500);
        connection.close();
        System.out.println("Main thread quitting.");
    }
}
