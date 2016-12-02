---
layout: tutorials
title: Persistence with Queues
summary: Learn how to set up persistence for guaranteed delivery.
icon: persistence-tutorial.png
---

This tutorial builds on the basic concepts introduced in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe), and will show you how to send and receive persistent messages from a Solace message router queue in a point to point fashion.

![]({{ site.baseurl }}/images/persistence-tutorial.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username
    *   Client-profile enabled with guaranteed messaging permissions.

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here]({{ site.docs-vmr-setup }}){:target="_top"}. By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to understand the following:

1.  How to create a durable queue on the Solace message router
2.  How to send a persistent message to a Solace queue
3.  How to bind to this queue and receive a persistent message

## Java Messaging Service (JMS) Introduction

JMS is a standard API for sending and receiving messages. As such, in addition to information provided on the Solace developer portal, you may also look at some external sources for more details about JMS. The following are good places to start

1.  [http://java.sun.com/products/jms/docs.html](http://java.sun.com/products/jms/docs.html){:target="_blank"}.
2.  [https://en.wikipedia.org/wiki/Java_Message_Service](https://en.wikipedia.org/wiki/Java_Message_Service){:target="_blank"}
3.  [https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3](https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3){:target="_blank"}

The oracle link points you to the JavaEE official tutorials which provide a good introduction to JMS. This getting started tutorial follows a similar path and shows you the Solace specifics that you need to do to get this working with your Solace message router.


## Solace message router properties

As with other tutorials, this tutorial will connect to the default message VPN of a Solace VMR which has authentication disabled. So the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument.

## Obtaining the Solace API

This tutorial depends on you having the Solace Messaging API for JMS. Here are a few easy ways to get the JMS API. The instructions in the [Building](#building) section assume you're using Gradle and pulling the jars from maven central. If your environment differs then adjust the build instructions appropriately.

### Get the API: Using Gradle

```
compile("com.solacesystems:sol-jms:10.+")
```

### Get the API: Using Maven

```
<dependency>
  <groupId>com.solacesystems</groupId>
  <artifactId>sol-jms</artifactId>
  <version>10.+</version>
</dependency>
```

### Get the API: Using the Solace Developer Portal

The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The JMS API is distributed as a zip file containing the required jars, API documentation, and examples. 

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples]({{ site.links-get-started }}){:target="_top"}.

At the end, this tutorial walks through downloading and running the sample from source.


## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples]({{ site.links-get-started }}){:target="_top"}.

To successfully build the samples you must have the Java API downloaded and available. The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The Java API is distributed as a zip file containing the required jars, API documentation, and examples.

At the end, this tutorial walks through downloading and running the sample from source.

## Configuring Solace JNDI

In order for JMS clients to successfully connect to a message broker like a Solace message router, they need to look up a connection factory object using the Java Naming and Directory Interface (JNDI) service. Solace message routers provide a JNDI lookup service to make this integration easy.

Additionally, this tutorial will make use of one JNDI topic for publishing and subscribing to messages. For simplicity in this tutorial, the following CLI script is provided. It will do the following:

*   Properly configure the default message-VPN with the necessary JNDI configuration.
*   Create the required durable Queue

The script configures these required JNDI resources:

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
        <td>Queue</td>
        <td>/JNDI/Q/tutorial</td>
    </tr>
</table>

The script also creates a durable queue named “Q/tutorial” and enables this queue. This script assumes the connection factory and queue do not exist and creates them. If the JNDI connection factory or queue already exists you may need to remove the keyword “create” from the script below.

```
home
enable
configure
message-spool message-vpn "default"
! pragma:interpreter:ignore-already-exists
  create queue "Q/tutorial" primary
! pragma:interpreter:no-ignore-already-exists
    access-type "exclusive"
    permission all "delete"
    no shutdown
    exit
  exit

jndi message-vpn "default"
  create connection-factory "/JNDI/CF/GettingStarted"
    property-list "messaging-properties"
        property "default-delivery-mode" "persistent"
        property "text-msg-xml-payload" "false"
        exit
    exit

  no queue "/JNDI/Q/tutorial"
  create queue "/JNDI/Q/tutorial"
      property "physical-name" "Q/tutorial"
      exit
  no shutdown
  exit
```

To apply this configuration, simply log in to the Solace message router CLI as an admin user. See the [VMR getting started]({{ site.docs-vmr-setup }}){:target="_top"} tutorial for default credentials and accounts. Then paste the above script into the CLI.

The first time you run this script you will see an error running this command:

```
solace(configure/jndi)#   no topic "/JNDI/Q/tutorial "
ERROR: Object '/JNDI/Q/tutorial' does not exist.
Command Failed
```

This error can safely be ignored. Future releases of the Solace message router will allow this script to be cleaned up further to avoid this error. Users can learn more details on Solace JMS and JNDI by referring to the [Solace JMS Documentaiton]({{ site.docs-jms-home }}){:target="_top"}.

## Connecting a session to the message router

As with other tutorials, this tutorial requires a JMS `Connection` to a Solace message router. So connect the JMS `Connection` as outlined in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

## Sending a message to a queue

Now it is time to send a message to the queue. 

![sending-message-to-queue]({{ site.baseurl }}/images/sending-message-to-queue-300x160.png)

There is no difference in the actual method calls to the JMS `MessageProducer` when sending a PERSISTENT message as compared to a NON-PERSISTENT message shown in the publish/subscribe tutorial. The difference in the PERSISTENT message is that the Solace message router will acknowledge the message once it is successfully stored on the message router and the `MessageProducer.send()` call will not return until it has successfully received this acknowledgement. This means that in JMS, all calls to the `MessageProducer.send()` are blocking calls and they wait for message confirmation from the Solace message router before proceeding. This is outlined in the JMS 1.1 specification and Solace JMS adheres to this requirement.

To send a message, you must still create a message. The difference from sending a NON-PERSISTENT message is that you must set the message delivery mode to PERSISTENT on send.

```java
Queue queue = (Queue)initialContext.lookup("/JNDI/Q/tutorial");
MessageProducer producer = session.createProducer(queue);
TextMessage message = session.createTextMessage("Hello world Queues!");
producer.send(queue,
              message,
              DeliveryMode.PERSISTENT,
              Message.DEFAULT_PRIORITY,
              Message.DEFAULT_TIME_TO_LIVE);
```

At this point the producer has sent a message to the Solace message router and it will be waiting for your consumer on the queue.

## Receiving a message from a queue

Now it is time to receive the messages sent to your queue.

![]({{ site.baseurl }}/images/receiving-message-from-queue-300x160.png)

You still need to a JMS `Connection` just as you did with the producer. With a connection, you then need to create a Session and bind to the Solace message router queue by creating a `MessageConsumer`. This is nearly identical to what was shown in the publish/subscribe tutorial. In this case, create a Session but use the Solace client acknowledgement mode. This allows the consumers to acknowledge each message individually without side-effects. You can learn more about acknowledgement modes in the Establishing Connections sections of [Solace JMS Messaging API Developer Guide – Establishing Connections]({{ site.docs-jms-connections }}){:target="_top"}.

```java
Session session = connection.createQueueSession(false, SupportedProperty.SOL_CLIENT_ACKNOWLEDGE);
```

Then your `MessageConsumer` can remain the same as before, only add a call to `message.acknowledge()` once you’re done processing the message.

```java
public void onMessage(Message message) {

    try {
        if (message instanceof TextMessage) {
            System.out.printf("TextMessage received: '%s'%n",
                    ((TextMessage)message).getText());
        } else {
            System.out.println("Message received.");
        }
        System.out.printf("Message Dump:%n%s%n",SolJmsUtility.dumpMessage(message));
        message.acknowledge();
        latch.countDown(); // unblock main thread
    } catch (JMSException e) {
        System.out.println("Error processing incoming message.");
        e.printStackTrace();
    }
}
```

## Summarizing

The full source code for this example is available in [GitHub]({{ site.repository }}){:target="_blank"}. If you combine the example source code shown above results in the following source:

*   [QueueProducer.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/QueueProducer.java){:target="_blank"}
*   [QueueConsumer.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/QueueConsumer.java){:target="_blank"}


### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
```

### Building

Building these examples is simple.  You can simply build the project using Gradle.

```
./gradlew assemble
```

This builds all of the JMS Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.


### Running the Sample

First start the `QueueProducer` to send a message to the queue. Then you can use the `QueueConsumer` sample to receive the messages from the queue.

```
$ ./build/staged/bin/queueProducer <HOST>
$ ./build/staged/bin/queueConsumer <HOST>
```

You have now successfully connected a client, sent persistent messages to a queue and received them from a consumer flow.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.
