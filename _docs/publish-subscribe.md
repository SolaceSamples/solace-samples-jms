---
layout: tutorials
title: Publish/Subscribe
summary: Learn how to set up pub/sub messaging on a Solace VMR.
icon: I_dev_P+S.svg
links:
    - label: TopicPublisher.java
      link: /blob/master/src/main/java/com/solace/samples/TopicPublisher.java
    - label: TopicSubscriber.java
      link: /blob/master/src/main/java/com/solace/samples/TopicSubscriber.java
---

This tutorial will introduce you to the fundamentals of the JMS 1.1 API as implemented by Solace. The tutorial will exemplify connecting a client, subscribing to a topic and sending a message matching this topic subscription. This forms the basis for any publish / subscribe message exchange.

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to Solace messaging with the following configuration details:
    *   Connectivity information for a Solace message-VPN
    *   Enabled client username and password

One simple way to get access to Solace messaging quickly is to create a messaging service in Solace Cloud [as outlined here]({{ site.links-solaceCloud-setup}}){:target="_top"}. You can find other ways to get access to Solace messaging below.

## Goals

The goal of this tutorial is to demonstrate the most basic messaging interaction using Solace. This tutorial will show you:

1.  How to build and send a message on a topic
2.  How to subscribe to a topic and receive a message

## Java Messaging Service (JMS) Introduction

JMS is a standard API for sending and receiving messages. As such, in addition to information provided on the Solace developer portal, you may also look at some external sources for more details about JMS. The following are good places to start

1.  [http://java.sun.com/products/jms/docs.html](http://java.sun.com/products/jms/docs.html){:target="_blank"}.
2.  [https://en.wikipedia.org/wiki/Java_Message_Service](https://en.wikipedia.org/wiki/Java_Message_Service){:target="_blank"}
3.  [https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3](https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3){:target="_blank"}

The oracle link points you to the JavaEE official tutorials which provide a good introduction to JMS. This getting started tutorial follows a similar path and shows you the Solace specifics that you need to do to get this working with Solace messaging.

{% include_relative assets/solaceMessaging.md %}
{% include_relative assets/solaceApi.md %}

## JMS administered objects

This tutorial will make use of two JMS administered objects:

*   A ConnectionFactory object – used by JMS clients to successfully connect to a message broker like a Solace message router
*   A Topic Destination – used for publishing and subscribing to messages. This example will use the topic `T/GettingStarted/pubsub`

The [JMS specification](http://java.sun.com/products/jms/docs.html){:target="_blank"} provides two ways to create administered objects:

*   JNDI Lookup
*   Programmatic creation through the JMS API.

This tutorial will use the approach of programmatically creating the required objects. For developers, this is the recommended approach as this enables:

*   Full control for the applications
*   No requirement to preconfigure the JNDI on Solace messaging or within an LDAP server
*   Easier integration into frameworks by avoiding external JNDI lookups.

The programmatic approach is also the convention most often followed with JMS samples. So it should be familiar to developers of JMS application. The Solace JMS API supports both programmatically creating administered objects and JNDI lookup. Developers can learn all about Solace JMS by referring to the [Solace JMS Documentation]({{ site.docs-jms-home }}){:target="_top"}.


## Connecting to Solace Messaging

In order to send or receive messages, an application must connect to Solace messaging. In JMS, a client connects by creating a `Connection` from the `ConnectionFactory`. Then a JMS `Session` is used as a factory for consumers and producers.

The following code shows how to create a connection using a programmatically created `ConnectionFactory`. You can learn more about other ways to create ConnectionFactories by referring to [Solace JMS Documentation - Obtaining Connection Factories]({{ site.docs-jms-obtaining-connection-factories }}){:target="_top"}.

```java

String[] split = args[1].split("@");

String host = args[0];
String vpnName = split[1];
String username = split[0];
String password = args[2];

SolConnectionFactory connectionFactory = SolJmsUtility.createConnectionFactory();
connectionFactory.setHost(host);
connectionFactory.setVPN(vpnName);
connectionFactory.setUsername(username);
connectionFactory.setPassword(password);

Connection connection = connectionFactory.createConnection();
Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
```

This tutorial uses an auto acknowledgement session. This is the simplest to use. However, it often makes sense to customize the acknowledgement mode in JMS to suit your application needs. Solace supports all of the JMS acknowledgement modes and introduces an extension which allows applications to individually acknowledge each message which we believe is a significant improvement of the behaviour of the default JMS client acknowledgement. Learn more in the [Solace JMS Documentation - Managing Sessions]({{ site.docs-jms-managing-sessions }}){:target="_top"}.

At this point your client is connected to Solace messaging. You can use SolAdmin to view the client connection and related details.

## Receiving a message

This tutorial uses JMS “Non-Persistent” messages which are at most once delivery messages. So first, let’s express interest in the messages by subscribing to a Solace topic. Then you can look at publishing a matching message and see it received.

With a session connected in the previous step, the next step is to create a message consumer. Message consumers enable the synchronous or asynchronous receipt of messages. Asynchronous receipt of messages is accomplished through callbacks. These callbacks are defined in JMS by the `MessageListener` interface.

![]({{ site.baseurl }}/assets/images/pub-sub-receiving-message-300x134.png)

First a `Topic` object is required. Here we create a topic from the JMS Session session programmatically. For other ways of obtaining a `Topic`, for example using JNDI, refer to the [Solace JMS Documentation - Working with Destinations]({{ site.docs-jms-working-with-destinations }}){:target="_top"}.

Then create the `MessageConsumer` using the JMS `Session`.

```java
final String TOPIC_NAME = "T/GettingStarted/pubsub";

Topic topic = session.createTopic(TOPIC_NAME);

MessageConsumer messageConsumer = session.createConsumer(topic);
```

Next, this sample will set the `MessageListener` callback which converts the message consumer from a blocking consumer into an asynchronous consumer. The message consumer callback code uses a countdown latch in this example to block the consumer thread until a single message has been received.

```java
final CountDownLatch latch = new CountDownLatch(1);

public void run(String... args) throws Exception {
...
    messageConsumer.setMessageListener(new MessageListener() {
        @Override
        public void onMessage(Message message) {
            try {
                if (message instanceof TextMessage) {
                    System.out.printf("TextMessage received: '%s'%n", ((TextMessage) message).getText());
                } else {
                    System.out.println("Message received.");
                }
                System.out.printf("Message Content:%n%s%n", SolJmsUtility.dumpMessage(message));
                latch.countDown(); // unblock the main thread
            } catch (JMSException ex) {
                System.out.println("Error processing incoming message.");
                ex.printStackTrace();
            }
        }
    });
...
```

For the purposes of demonstration, the callback will simply receive the message and print its contents to the console before signalling the main thread via the countdown latch.

Finally, start the connection so that messages will start flowing to the consumer.

```java
connection.start();

latch.await();
```

At this point the consumer is ready to receive messages.

## Sending a message

Now it is time to send a message to the waiting consumer.

![]({{ site.baseurl }}/assets/images/pub-sub-sending-message-300x134.png)

### Establishing the publisher flow

In JMS, a message producer is required for sending messages to Solace messaging.

```java
final String TOPIC_NAME = "T/GettingStarted/pubsub";

Topic topic = session.createTopic(TOPIC_NAME);
MessageProducer messageProducer = session.createProducer(topic);
```

JMS Message Producers are created from the session object and are assigned a default destination on creation.

### Creating and sending the message

To send a message, first create a message from the JMS `Session`. Then use the MessageProducer to send the message. The message producer offers several options for sending. Since we wish to send a non-persistent message in this tutorial, we will use the most flexible option where delivery mode, priority and time to live is specified.

```java
TextMessage message = session.createTextMessage("Hello world!");
messageProducer.send(topic, message, DeliveryMode.NON_PERSISTENT,
        Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
```

At this point the producer has sent a message to Solace messaging and your waiting consumer will have received the message and printed its contents to the screen.

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

<ul>
{% for item in page.links %}
<li><a href="{{ site.repository }}{{ item.link }}" target="_blank">{{ item.label }}</a></li>
{% endfor %}
</ul>

### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.repository | split: '/' | last }}
```

### Building

Building these examples is simple.  You can simply build the project using Gradle.

```
./gradlew assemble
```

This builds all of the JMS Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

If you start the `TopicSubscriber`, with the required arguments of your Solace messaging, it will connect and wait for a message.

```
$ ./build/staged/bin/topicSubscriber <host:port> <client-username>@<message-vpn> <client-password>
TopicSubscriber is connecting to Solace messaging at <host:port>...
Connected to Solace Message VPN <message-vpn> with client username '<client-username>'.
Awaiting message...
```

Note: log4j logs were omitted in the above to remain concise.

Then you can send a message using the `TopicPublisher` with the same arguments. If successful, the output for the producer will look like the following:

```
$ ./build/staged/bin/topicPublisher <host:port> <client-username>@<message-vpn> <client-password>
TopicPublisher is connecting to Solace messaging at <host:port>...
Connected to the Solace Message VPN <message-vpn> with client username <client-username>.
Sending message 'Hello world!' to topic 'T/GettingStarted/pubsub'...
Sent successfully. Exiting...
```

With the message delivered the subscriber output will look like the following:

```
TextMessage received: 'Hello world!'
Message Content:
JMSDeliveryMode:                        1
JMSDestination:                         Topic 'T/GettingStarted/pubsub'
JMSExpiration:                          0
JMSMessageID:                           ID:fe80:0:0:0:0:5efe:c0a8:4101%net13fbd815d6024960d0:0
JMSPriority:                            0
JMSTimestamp:                           1500556596909
JMSProperties:                          {JMSXUserID:clientUsername,JMS_Solace_isXML:true,JMS_Solace_DeliverToOne:false,JMS_Solace_DeadMsgQueueEligible:false,JMS_Solace_ElidingEligible:false,JMS_Solace_MsgDiscardIndication:false,Solace_JMS_Prop_IS_Reply_Message:false}
Destination:                            Topic 'T/GettingStarted/pubsub'
AppMessageID:                           ID:fe80:0:0:0:0:5efe:c0a8:4101%net13fbd815d6024960d0:0
SendTimestamp:                          1500556596909 (Thu Jul 20 2017 09:16:36)
Priority:                               0
Class Of Service:                       USER_COS_1
DeliveryMode:                           DIRECT
Message Id:                             4
User Property Map:                      1 entries
  Key 'JMSXUserID' (String): clientUsername

XML:                                    len=12
  48 65 6c 6c 6f 20 77 6f    72 6c 64 21                Hello.world!
```

The received message is printed to the screen. The `TextMessage` contents was “Hello world!” as expected and the message dump contains extra information about the Solace message that was received.

You have now successfully connected a client, subscribed to a topic and exchanged messages using this topic.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
