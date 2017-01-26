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

import java.util.Hashtable;

import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.solacesystems.jms.SupportedProperty;

public class QueueProducer {

    public void run(String... args) throws Exception {

        System.out.println("QueueProducer initializing...");

        // The client needs to specify all of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String) args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
        env.put(Context.SECURITY_PRINCIPAL, "clientUsername");
        env.put(Context.SECURITY_CREDENTIALS, "password");

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        QueueConnectionFactory cf = (QueueConnectionFactory) initialContext.lookup("/JNDI/CF/GettingStarted");
        // JMS Connection
        QueueConnection connection = cf.createQueueConnection();

        // Create a non-transacted, Auto Ack session.
        Session session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Queue.
        Queue queue = (Queue) initialContext.lookup("/JNDI/Q/tutorial");

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
        initialContext.close();
    }

    public static void main(String... args) throws Exception {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: QueueProducer <msg_backbone_ip:port>");
            System.exit(-1);
        }

        QueueProducer app = new QueueProducer();
        app.run(args);
    }
}
