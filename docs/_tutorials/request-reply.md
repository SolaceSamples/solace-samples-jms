---
layout: tutorials
title: Request/Reply
summary: Learn how to set up request/reply messaging.
icon: request-reply.png
---


This tutorial outlines both roles in the request-response message exchange pattern. It will show you how to act as the client by creating a request, sending it and waiting for the response. It will also show you how to act as the server by receiving incoming requests, creating a reply and sending it back to the client. It builds on the basic concepts introduced in [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

![]({{ site.baseurl }}/images/request-reply.png)

## Assumptions

This tutorial assumes the following:

*   You are familiar with Solace [core concepts]({{ site.docs-core-concepts }}){:target="_top"}.
*   You have access to a running Solace message router with the following configuration:
    *   Enabled message VPN
    *   Enabled client username

One simple way to get access to a Solace message router is to start a Solace VMR load [as outlined here]({{ site.docs-vmr-setup }}){:target="_top"}. By default the Solace VMR will run with the “default” message VPN configured and ready for messaging. Going forward, this tutorial assumes that you are using the Solace VMR. If you are using a different Solace message router configuration, adapt the instructions to match your configuration.

The build instructions in this tutorial assume you are using a Linux shell. If your environment differs, adapt the instructions.

## Goals

The goal of this tutorial is to understand the following:

*   On the requestor side:
    1.  How to create a request
    2.  How to receive a response
    3.  How to use the Solace JMS API to correlate the request and response
*   On the replier side:
    1.  How to detect a request expecting a reply
    2.  How to generate a reply message

## Java Messaging Service (JMS) Introduction

JMS is a standard API for sending and receiving messages. As such, in addition to information provided on the Solace developer portal, you may also look at some external sources for more details about JMS. The following are good places to start

1.  [http://java.sun.com/products/jms/docs.html](http://java.sun.com/products/jms/docs.html){:target="_blank"}.
2.  [https://en.wikipedia.org/wiki/Java_Message_Service](https://en.wikipedia.org/wiki/Java_Message_Service){:target="_blank"}
3.  [https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3](https://docs.oracle.com/javaee/7/tutorial/partmessaging.htm#GFIRP3){:target="_blank"}

The oracle link points you to the JavaEE official tutorials which provide a good introduction to JMS. This getting started tutorial follows a similar path and shows you the Solace specifics that you need to do to get this working with your Solace message router.

## Overview

Request-reply messaging is supported by the Solace message router for all delivery modes. The JMS API does provide a `TopicRequestor` and `QueueRequestor` interface which is very simple. However, this interface lacks the ability to timeout the requests. This limitation means that it is often simpler to implement the request – reply pattern in your application. This tutorial will follow this approach.

It is also possible to use guaranteed messaging for request reply scenarios. In this case the replier can listen on a queue for incoming requests and the requestor can use a temporary endpoint to attract replies. This is explained further in the [Solace product documentation]({{ site.docs-gm-rr }}){:target="_top"} and shown in the API samples named `SolJMSRRGuaranteedRequestor` and `SolJMSRRGuaranteedReplier`.

### Message Correlation

For request-reply messaging to be successful it must be possible for the requestor to correlate the request with the subsequent reply. Solace messages support two fields that are needed to enable request-reply correlation. The reply-to field can be used by the requestor to indicate a Solace Topic or Queue where the reply should be sent. In JMS, a natural choice for this is a temporary queue. The second requirement is to be able to identify the reply message within the stream of incoming messages. This is accomplished using the correlation-id field. This field will transit the Solace messaging system unmodified. Repliers can include the same correlation-id in a reply message to allow the requestor to detect the corresponding reply. The figure below outlines this exchange.

![]({{ site.baseurl }}/images/Request-Reply_diagram-1.png)

Note: In JMS it also common for the requestor to put a unique message ID into the message on send and have the replier respond with this message ID in the correlation ID field of the response message. This is equally possible with the Solace JMS API. This tutorial favors the correlation ID approach because it works commonly with all Solace messaging APIs.

## Trying it yourself

This tutorial is available in [GitHub]({{ site.repository }}){:target="_blank"} along with the other [Solace Developer Getting Started Examples]({{ site.links-get-started }}){:target="_top"}.

To successfully build the samples you must have the JMS API downloaded and available. The JMS API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The JMS API is distributed as a zip file containing the required jars, API documentation, and examples.

At the end, this tutorial walks through downloading and running the sample from source.

## Configuring Solace JNDI

As described in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe) in order for JMS clients to successfully connect to a message broker like a Solace message router, they need to look up a connection factory object using the Java Naming and Directory Interface (JNDI) service. Solace message routers provide a JNDI lookup service to make this integration easy.

Additionally, this tutorial will make use of one JNDI topic of requests. For simplicity in this tutorial, the following CLI script is provided. It will properly configure the default message-VPN with the necessary JNDI configuration. It assumes the connection factory and topic do not exist and creates them. If the JNDI connection factory or topic already exists you may need to remove the keyword “create” from the script below.

The script configures two required resources.

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
        <td>/JNDI/T/GettingStarted/requests</td>
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
    create topic "/JNDI/T/GettingStarted/requests"
        property "physical-name" "T/GettingStarted/requests"
        exit
    no shutdown
    exit
```

To apply this configuration, simply log in to the Solace message router CLI as an admin user. See the [VMR getting started]({{ site.docs-vmr-setup }}){:target="_top"} tutorial for default credentials and accounts. Then paste the above script into the CLI.

Users can learn more details on Solace JMS and JNDI by referring to the [SolAdmin User Guide – Configuring JMS Objects]({{ site.docs-jms-home }}){:target="_top"}.

## Connecting a session to the message router

As with other tutorials, this tutorial requires a JMS `Connection` connected to the default message VPN of a Solace VMR which has authentication disabled. So the only required information to proceed is the Solace VMR host string which this tutorial accepts as an argument. Connect the JMS `Connection` as outlined in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

## Making a request

First let’s look at the requestor. This is the application that will send the initial request message and wait for the reply.  
![]({{ site.baseurl }}/images/Request-Reply_diagram-2.png)

In order to be able to receive the response message back from the Replier, the Requestor must setup a JMS `Consumer`. For simplicity, this tutorial will use a blocking Consumer to receive the response messages using a temporary queue.

```java
TemporaryQueue replyToQueue = session.createTemporaryQueue();
final MessageConsumer consumer = session.createConsumer(replyToQueue);
connection.start();
```

With the connection started, now the Requestor is ready to receive any reply messages on its temporary JMS Queue. Next you must create a message and the topic to send the message to. This is done in the same way as illustrated in the [publish/subscribe tutorial]({{ site.baseurl }}/publish-subscribe).

```java
final Topic requestDestination = (Topic)initialContext.lookup("/JNDI/T/GettingStarted/requests");
TextMessage request = session.createTextMessage();
final String text = "Sample Request";
request.setText(text);

request.setJMSReplyTo(replyToQueue);
String correlationId = UUID.randomUUID().toString();
request.setJMSCorrelationID(correlationId);
```

The difference in the above versus the publish/subscribe tutorial is that now you are setting the `JMSReplyTo` and `JMSCorrelationID` fields of the message. The `JMSReplyTo` specifies how the Replier application should respond. The above code is using the temporary queue that was just created. The `JMSCorrelationID` is the unique ID used to correlate requests with responses. The above code is simply using a randomly generated unique identifier.

Finally send the request and wait for the response. This example demonstrates a blocking call where the method will wait for the response message to be received.

```java
final int timeoutMs = 10000;
producer.send(requestDestination,
              request,
              DeliveryMode.NON_PERSISTENT,
              Message.DEFAULT_PRIORITY,
              Message.DEFAULT_TIME_TO_LIVE);
Message reply = consumer.receive(timeoutMs);

if (reply == null) {
    System.out.println("Failed to receive a reply in " + timeoutMs + " msecs");
    return;
}
```

If no response is received within the timeout specified (10 seconds in this example), then the API will return a null message.

## Replying to a request

Now it is time to receive the request and generate an appropriate reply.

![Request-Reply_diagram-3]({{ site.baseurl }}/images/Request-Reply_diagram-3.png)

Just as with previous tutorials, you still need to connect a JMS Connection and Session and create a MessageConsumer to receive request messages. However, in order to send replies back to the requestor, you will also need a MessageProducer. The following code will create the producer and consumer that is required.

```java
final Topic topic = (Topic)initialContext.lookup("/JNDI/T/GettingStarted/requests");
final MessageProducer producer = session.createProducer(topic);
final MessageConsumer consumer = session.createConsumer(topic);
```

Then you simply have to modify the `onReceive()` method of the `MessageConsumer` from the publish/subscribe tutorial to inspect incoming messages and generate appropriate replies.

For example, the following code will send a response to all messages that have a reply-to field. It will copy over any `JMSCorrelationID` found in the incoming messages. It will also set a Solace specific boolean field indicating this message is a reply message. This is a Solace extension to enable JMS applications to exchange requests and replies easily with applications using other Solace APIs.

```java
public void onMessage(Message request) {
    Destination replyDestination;
    try {
        replyDestination = request.getJMSReplyTo();
        if (replyDestination != null) {
            TextMessage reply = session.createTextMessage();
            final String text = "Sample response";
            reply.setText(text);
            reply.setJMSCorrelationID(request.getJMSCorrelationID());
             reply.setBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE,

                         Boolean.TRUE);

            producer.send(replyDestination,
                          reply,
                          DeliveryMode.NON_PERSISTENT,
                          Message.DEFAULT_PRIORITY,
                          Message.DEFAULT_TIME_TO_LIVE);
            }

    } catch (JMSException e) {
        System.out.println("Error processing incoming message.");
        e.printStackTrace();
    }

}
```

## Receiving the Reply Message

All that’s left is to receive and process the reply message as it is received at the requestor. If you now add the following to your Requestor code you will see each reply printed to the console.

```java
if (reply.getJMSCorrelationID() == null) {
    throw new Exception("Received a reply message with no correlationID. This field is needed for a direct request.");
}

//The reply's correlationID should match the request's correlationID
if (!reply.getJMSCorrelationID().equals(correlationId)) {
    throw new Exception("Received invalid correlationID in reply message.");
}

if (reply instanceof TextMessage) {
    System.out.printf("TextMessage response received: '%s'%n",
        ((TextMessage)reply).getText());
    if     (!reply.getBooleanProperty(SupportedProperty.SOLACE_JMS_PROP_IS_REPLY_MESSAGE)) {

        System.out.println("Warning: Received a reply message without the isReplyMsg flag set.");
    }
}

System.out.printf("Response Message Dump:%n%s%n",SolJmsUtility.dumpMessage(reply));
```

## Summarizing

Combining the example source code show above results in the following source code files:

*   [BasicRequestor.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/BasicRequestor.java){:target="_blank"}
*   [BasicReplier.java]({{ site.repository }}/blob/master/src/main/java/com/solace/samples/BasicReplier.java){:target="_blank"}

### Getting the Source

Clone the GitHub repository containing the Solace samples.

```
git clone {{ site.repository }}
cd {{ site.baseurl | remove: '/'}}
```

### Building

Building these examples is simple.  Download and unpacked the Java API library to a known location. Then copy the contents of the `sol-jcsmp-VERSION/lib` directory to a `libs` sub-directory in your `{{ site.baseurl | remove: '/'}}`.

In the following command line replace VERSION with the Solace API version you downloaded.

```
mkdir libs
cp  ../sol-jcsmp-VERSION/lib/* libs
```

Now you can simply build the project using Gradle.

```
./gradlew assemble
```

This build all of the Java Getting Started Samples with OS specific launch scripts. The files are staged in the `build/staged` directory.

### Running the Sample

First start the `BasicReplier` so that it is up and listening for requests. Then you can use the `BasicRequestor` sample to send requests and receive replies.

```
$ ./build/staged/bin/basicReplier <HOST>
$ ./build/staged/bin/basicRequestor <HOST>
```

With that you now know how to successfully implement the request-reply message exchange pattern using JMS NON-PERSISTENT messages and temporary endpoints.

If you have any issues sending and receiving a message, check the [Solace community]({{ site.links-community }}){:target="_top"} for answers to common issues.