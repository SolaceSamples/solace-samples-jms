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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

public class TopicPublisher {

    public void run(String... args) throws Exception {

        System.out.println("TopicPublisher initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory cf = SolJmsUtility.createConnectionFactory();
        cf.setHost((String) args[0]);
        cf.setVPN("default");
        cf.setUsername("clientUsername");
        cf.setPassword("password");
        
        // JMS Connection
        Connection connection = cf.createConnection();

        // Create a non-transacted, Auto Ack session.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Create the topic programmatically
        Topic publishDestination = session.createTopic("T/GettingStarted/pubsub");

        final MessageProducer producer = session.createProducer(publishDestination);
        
        TextMessage message = session.createTextMessage("Hello world!");

        System.out.printf("Connected. About to send request message '%s' to topic '%s'...%n", message.getText(),
                publishDestination.toString());

        // Leaving priority and Time to Live to their defaults.
        // NOTE: Priority is not supported by the Solace Message Bus
        producer.send(publishDestination, message, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                Message.DEFAULT_TIME_TO_LIVE);

        // Close consumer
        connection.close();
        System.out.println("Message sent. Exiting.");
    }

    public static void main(String... args) throws Exception {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: TopicPublisher <msg_backbone_ip:port>");
            System.exit(-1);
        }

        TopicPublisher app = new TopicPublisher();
        app.run(args);
    }
}
