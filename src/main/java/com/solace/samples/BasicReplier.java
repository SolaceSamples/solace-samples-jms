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

import java.io.IOException;
import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.solacesystems.jms.SupportedProperty;

public class BasicReplier {

    public void run(String... args) throws JMSException, NamingException {
        System.out.println("BasicReplier initializing...");

        // The client needs to specify both of the following properties:
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
        env.put(InitialContext.PROVIDER_URL, (String) args[0]);
        env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
        env.put(Context.SECURITY_PRINCIPAL, "clientUsername");

        // InitialContext is used to lookup the JMS administered objects.
        InitialContext initialContext = new InitialContext(env);
        // Lookup ConnectionFactory.
        ConnectionFactory cf = (ConnectionFactory) initialContext.lookup("/JNDI/CF/GettingStarted");
        // JMS Connection
        Connection connection = cf.createConnection();

        // Create a non-transacted, Auto Ack session.
        final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Lookup Topic in Solace JNDI.
        final Topic topic = (Topic) initialContext.lookup("/JNDI/T/GettingStarted/requests");

        final MessageProducer producer = session.createProducer(topic);

        final MessageConsumer consumer = session.createConsumer(topic);

        /** Anonymous inner-class for request handling **/
        consumer.setMessageListener(new MessageListener() {

            @Override
            public void onMessage(Message request) {

                Destination replyDestination;
                try {
                    replyDestination = request.getJMSReplyTo();

                    if (replyDestination != null) {
                        System.out.println("Received request, generating response");
                        TextMessage reply = session.createTextMessage();

                        final String text = "Sample response";
                        reply.setText(text);

                        // Copy the correlation ID from the request to the reply
                        reply.setJMSCorrelationID(request.getJMSCorrelationID());

                        // For direct messaging only, this flag is needed to
                        // interoperate with
                        // Solace Java, C, and C# request reply APIs.
                        reply.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE, Boolean.TRUE);

                        // Leaving priority and Time to Live to their defaults.
                        // NOTE: Priority is not supported by the Solace Message
                        // Routers
                        producer.send(replyDestination, reply, DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY,
                                Message.DEFAULT_TIME_TO_LIVE);

                    } else {
                        System.out.println("Received message without reply-to field");
                    }
                } catch (JMSException e) {
                    System.out.println("Error processing incoming message.");
                    e.printStackTrace();
                }

            }
        });
        ;

        // Start receiving messages
        connection.start();

        // Consume-only session is now hooked up and running!
        System.out.println("Listening for request messages on topic " + topic + " ... Press enter to exit");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close consumer
        connection.close();
        initialContext.close();
        System.out.println("Exiting.");
    }

    public static void main(String... args) throws JMSException, NamingException {

        // Check command line arguments
        if (args.length < 1) {
            System.out.println("Usage: BasicReplier <msg_backbone_ip:port>");
            System.exit(-1);
        }

        BasicReplier app = new BasicReplier();
        app.run(args);
    }
}
