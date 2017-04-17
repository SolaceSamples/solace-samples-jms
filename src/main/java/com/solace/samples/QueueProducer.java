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

import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TextMessage;
import com.solacesystems.jms.SolConnectionFactory;
import com.solacesystems.jms.SolJmsUtility;

public class QueueProducer {

    public void run(String... args) throws Exception {

        System.out.println("QueueProducer initializing...");

        // Programmatically create the connection factory using default settings
        SolConnectionFactory cf = SolJmsUtility.createConnectionFactory();
        cf.setHost((String) args[0]);
        // use default message-VPN unless specified
        cf.setVPN(args.length > 1 && args[1] != null ? args[1] : "default");
        cf.setUsername("clientUsername");
        cf.setPassword("password");
 
        // Enables persistent queues or topic endpoints to be created dynamically
        // on the router, used when Session.createQueue() is called below
        cf.setDynamicDurables(true);

        // JMS Connection
        QueueConnection connection = cf.createQueueConnection();

        // Create a non-transacted, Auto Ack session.
        Session session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        // Create the queue programmatically and the corresponding router resource
        // will also be created dynamically because DynamicDurables is enabled.
        Queue queue = session.createQueue("Q/tutorial");

        // From the session, create a producer for the destination.
        // Use the default delivery mode as set in the connection factory
        MessageProducer producer = session.createProducer(queue);

        // Create a text message.
        TextMessage message = session.createTextMessage("Hello world Queues!");

        System.out.printf("Connected. About to send message '%s' to queue '%s'...%n", message.getText(),
                queue.toString());

        producer.send(queue, message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

        System.out.println("Message sent. Exiting.");

        connection.close();
    }

    public static void main(String... args) throws Exception {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: QueueProducer <msg_backbone_ip:port> " +
            		"[<message-vpn>]");
            System.exit(-1);
        }

        QueueProducer app = new QueueProducer();
        app.run(args);
    }
}
