---
layout: tutorials
title: Publish/Subscribe
summary: Learn how to set up pub/sub messaging on a Solace VMR.
icon: publish-subscribe.png
---

This tutorial will introduce you to the fundamentals of the JMS 1.1 API as implemented by Solace. The tutorial will exemplify connecting a client, subscribing to a topic and sending a message matching this topic subscription. This forms the basis for any publish / subscribe message exchange illustrated here:

![]({{ site.baseurl }}/images/publish-subscribe.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts](http://dev.solacesystems.com/docs/core-concepts/){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here](http://dev.solacesystems.com/docs/get-started/setting-up-solace-vmr_vmware/){:target="_top"}. By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to demonstrate the most basic messaging interaction using Solace. This tutorial will show you:

1.  How to build and send a message on a topic
2.  How to subscribe to a topic and receive a message

## Java Messaging Service (JMS) Introduction

JMS is a standard API for sending and receiving messages. As such, in addition to information provided on the Solace developer portal, you may also look at some external sources for more details about JMS. The following are good places to start

1.  [http://java.sun.com/products/jms/docs.html](http://java.sun.com/products/jms/docs.html){:target="_blank"}.
2.  [https://en.wikipedia.org/wiki/Java_Message_Service](https://en.wikipedia.org/wiki/Java_Message_Service){:target="_blank"}
3.  [https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3](https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3){:target="_blank"}

The oracle link points you to the JavaEE official tutorials which provide a good introduction to JMS. This getting started tutorial follows a similar path and shows you the Solace specifics that you need to do to get this working with your Solace message router.

## Solace message router properties

In order to send or receive messages to a Solace message router, you need to know a few details of how to connect to the Solace message router. Specifically you need to know the following:

<table>
  <tr>
    <th>Resource</th>
    <th>Value</th>
    <th>Description</th>
  </tr>
  <tr>
    <td>Host</td>
    <td>String of the form <code>smf://DNS_NAME</code> or <code>smf://IP:Port</code></td>
    <td>This is the address clients use when connecting to the Solace message router to send and receive messages. For a Solace VMR this there is only a single interface so the IP is the same as the management IP address. For Solace message router appliances this is the host address of the message-backbone.</td>
  </tr>
  <tr>
    <td>Message VPN</td>
    <td>String</td>
    <td>The Solace message router Message VPN that this client should connect to. The simplest option is to use the “default” message-vpn which is present on all Solace message routers and fully enabled for message traffic on Solace VMRs.</td>
  </tr>
  <tr>
    <td>Client Username</td>
    <td>String</td>
    <td>The client username. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value.</td>
  </tr>
  <tr>
    <td>Client Password</td>
    <td>String</td>
    <td>The optional client password. For the Solace VMR default message VPN, authentication is disabled by default, so this can be any value or omitted.</td>
  </tr>
</table>


For the purposes of this tutorial, you will connect to the default message VPN of a Solace VMR so the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument.

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples](http://dev.solacesystems.com/get-started/jms-tutorials/){:target="_top"}.

To successfully build the samples you must have the JMS API downloaded and available. The JMS API library can be [downloaded here](http://dev.solacesystems.com/downloads/){:target="_top"}. The JMS API is distributed as a zip file containing the required jars, API documentation, and examples.

At the end, this tutorial walks through downloading and running the sample from source.

## Configuring Solace JNDI

In order for JMS clients to successfully connect to a message broker like a Solace message router, they need to look up a connection factory object using the Java Naming and Directory Interface (JNDI) service. Solace message routers provide a JNDI lookup service to make this integration easy.

Additionally, this tutorial will make use of one JNDI topic for publishing and subscribing to messages. For simplicity in this tutorial, the following CLI script is provided. It will properly configure the default message-VPN with the necessary JNDI configuration.

The script configures two required resources. This script assumes the connection factory and topic do not exist and creates them. If the JNDI connection factory or topic already exists you may need to remove the keyword “create” from the script below.

<table>
    <tr>
        <th>Resource</th>
        <th>Value</th>
    </tr>
    <tr>
        <td>Connection Factory</td>
        <td>/JNDI/CF/GettingStarted</td>
    </tr>
    <tr>
        <td>Topic</td>
        <td>/JNDI/T/GettingStarted/pubsub</td>
    </tr>
</table>

```
home
enable
configure
jndi message-vpn "default"
    create connection-factory "/JNDI/CF/GettingStarted"
        property-list "messaging-properties"
            property "default-delivery-mode" "persistent"
             property "text-msg-xml-payload" "false"
             exit
        exit
    create topic "/JNDI/T/GettingStarted/pubsub"
         property "physical-name" "T/GettingStarted/pubsub"
         exit
    no shutdown
    exit
```

To apply this configuration, simply log in to the Solace message router CLI as an admin user. See the [VMR getting started](http://dev.solacesystems.com/docs/get-started/setting-up-solace-vmr_vmware/){:target="_top"} tutorial for default credentials and accounts. Then paste the above script into the CLI.

Users can learn more details on Solace JMS and JNDI by referring to the [SolAdmin User Guide – Configuring JMS Objects](https://sftp.solacesystems.com/Portal_Docs/#page/SolAdmin_User_Guide/Configuring_JMS_Objects.html#){:target="_top"}.

## Connecting to the Solace message router

In order to send or receive messages, an application must connect to the Solace message router. In JMS, a client connects by creating a `Connection` from the `ConnectionFactory`. Then a JMS `Session` is used to as a factory for consumers and producers. The following code shows how to create a connection using Solace JNDI.

```java
Hashtable<String, Object> env = new Hashtable<String, Object>();
env.put(InitialContext.INITIAL_CONTEXT_FACTORY, "com.solacesystems.jndi.SolJNDIInitialContextFactory");
env.put(InitialContext.PROVIDER_URL, (String)args[0]);
env.put(SupportedProperty.SOLACE_JMS_VPN, "default");
env.put(Context.SECURITY_PRINCIPAL, "clientUsername");

InitialContext initialContext = new InitialContext(env);
ConnectionFactory cf = (ConnectionFactory)initialContext.lookup("/JNDI/CF/GettingStarted");

Connection connection = cf.createConnection();
final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
```

This tutorial uses an auto acknowledgement session. This is the simplest to use. However, it often makes sense to customize the acknowledgement mode in JMS to suit your application needs. Solace supports all of the JMS acknowledgement modes and introduces an extension which allows applications to individually acknowledge each message which we believe is a significant improvement of the behaviour of the default JMS client acknowledgement. Learn more in the [Solace JMS Messaging API Developer Guide – Establishing Connections](https://sftp.solacesystems.com/Portal_Docs/#page/Solace_JMS_Messaging_API_Developer_Guide/Establishing_Connections.html#).

At this point your client is connected to the Solace message router. You can use SolAdmin to view the client connection and related details.

## Receiving a message

This tutorial uses JMS “Non-Persistent” messages which are at most once delivery messages. So first, let’s express interest in the messages by subscribing to a Solace topic. Then you can look at publishing a matching message and see it received.

With a session connected in the previous step, the next step is to create a message consumer. Message consumers enable the synchronous or asynchronous receipt of messages. Asynchronous receipt of messages is accomplished through callbacks. These callbacks are defined in JMS by the `MessageListener` interface.

![]({{ site.baseurl }}/images/pub-sub-receiving-message-300x134.png)

First, look up the topic in the Solace JNDI. Alternatively JMS allows for topics to be created using the session. Creating topics from the session is easier because it involves fewer configuration steps but it is less portable.

Then create the `MessageConsumer` using the JMS `Session`.

```java
// Lookup Topic in Solace JNDI.
final Topic topic = (Topic)initialContext.lookup("/JNDI/T/GettingStarted/pubsub");
final MessageConsumer consumer = session.createConsumer(topic);
```

Next, this sample will set the `MessageListener` callback which converts the message consumer from a blocking consumer into an asynchronous consumer. The message consumer callback code uses a countdown latch in this example to block the consumer thread until a single message has been received.

```java
// Latch used for synchronizing b/w threads
final CountDownLatch latch = new CountDownLatch(1);

/** Anonymous inner-class for receiving messages **/
consumer.setMessageListener(new MessageListener() {

    @Override
    public void onMessage(Message message) {

        try {
            if (message instanceof TextMessage) {
                System.out.printf("TextMessage received: '%s'%n",
                    ((TextMessage)message).getText());
            } else {
                System.out.println("Message received.");
            }
            System.out.printf("Message Dump:%n%s%n",
                                SolJmsUtility.dumpMessage(message));
            latch.countDown(); // unblock main thread
        } catch (JMSException e) {
            System.out.println("Error processing incoming message.");
            e.printStackTrace();
        }
    }
});
```

For the purposes of demonstration, the callback will simply receive the message and print its contents to the console before signalling the main thread via the countdown latch.

Finally, start the connection so that messages will start flowing to the consumer.

```java
connection.start();

try {
    latch.await(); // block here until message received, and latch will flip
} catch (InterruptedException e) {
    System.out.println("I was awoken while waiting");
}
```

At this point the consumer is ready to receive messages.

## Sending a message

Now it is time to send a message to the waiting consumer.

![]({{ site.baseurl }}/images/pub-sub-sending-message-300x134.png)

### Establishing the publisher flow

In JMS, a message producer is required for sending messages to a Solace message router.

```java
// Lookup Topic in Solace JNDI.
final Topic publishDestination = (Topic)initialContext.lookup( "/JNDI/T/GettingStarted/pubsub" );

final MessageProducer producer = session.createProducer(publishDestination);
```

JMS Message Producers are created from the session object and are assigned a default destination on creation.

### Creating and sending the message

To send a message, first create a message from the JMS `Session`. Then use the MessageProducer to send the message. The message producer offers several options for sending. Since we wish to send a non-persistent message in this tutorial, we will use the most flexible option where delivery mode, priority and time to live is specified.

```java
TextMessage message = session.createTextMessage("Hello world!");
producer.send(publishDestination,
            message,
            DeliveryMode.NON_PERSISTENT,
            Message.DEFAULT_PRIORITY,
            Message.DEFAULT_TIME_TO_LIVE);
```

At this point the producer has sent a message to the Solace message router and your waiting consumer will have received the message and printed its contents to the screen.

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

*   [TopicPublisher.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/TopicPublisher.java){:target="_blank"}
*   [TopicSubscriber.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/TopicSubscriber.java){:target="_blank"}

### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
```

### Building

Building these examples is simple.  Download and unpacked the JMS API library to a known location. Then copy the contents of the `sol-jms-VERSION/lib` directory to a `libs` sub-directory in your `{{ site.baseurl | remove: '/'}}`.

In the following command line replace VERSION with the Solace API version you downloaded.

```
mkdir libs
cp  ../sol-jms-VERSION/lib/* libs
```

Now you can simply build the project using Gradle.

```
./gradlew assemble
```

This build all of the Java Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

If you start the `TopicSubscriber` with a single argument for the Solace message router host address it will connect and wait for a message.

```
$ ./build/staged/bin/topicSubscriber <HOST>
TopicSubscriber initializing...
Connected. Awaiting message...
```

Note: log4j logs were omitted in the above to remain concise.

Then you can send a message using the `TopicPublisher` again using a single argument to specify the Solace message router host address. If successful, the output for the producer will look like the following:

```
$ ./build/staged/bin/topicPublisher <HOST>
Topic Publisher initializing...
Connected. About to send message 'Hello world!' to topic 'T/GettingStarted/pubsub'...
Message sent. Exiting.
```

With the message delivered the subscriber output will look like the following:

```
TextMessage received: 'Hello world!'
Message Dump:
JMSDeliveryMode:                         1
JMSDestination:                           Topic 'T/GettingStarted/pubsub'
JMSExpiration:                           0
JMSMessageID:                             ID:fe80:0:0:0:211:43ff:fee3:4821%2876815048f6d5d70:0
JMSPriority:                             0
JMSTimestamp:                             1444333147767
JMSProperties:                           {JMS_Solace_isXML:false,JMS_Solace_DeliverToOne:false,JMS_Solace_DeadMsgQueueEligible:false,JMS_Solace_ElidingEligible:false,Solace_JMS_Prop_IS_Reply_Message:false}
Destination:                             Topic 'T/GettingStarted/pubsub'
AppMessageID:                             ID:fe80:0:0:0:211:43ff:fee3:4821%2876815048f6d5d70:0
SendTimestamp:                           1444333147767 (Thu Oct 08 2015 15:39:07)
Class Of Service:                         USER_COS_1
DeliveryMode:                             NON_PERSISTENT
Message Id:                               4
Binary Attachment:                       len=15
1c 0f 48 65 6c 6c 6f 20   77 6f 72 6c 64 21 00       ..Hello.world!.
```

The received message is printed to the screen. The `TextMessage` contents was “Hello world!” as expected and the message dump contains extra information about the Solace message that was received.

You have now successfully connected a client, subscribed to a topic and exchanged messages using this topic.

If you have any issues sending and receiving a message, check the [Solace community](http://dev.solacesystems.com/community/){:target="_top"} for answers to common issues.


