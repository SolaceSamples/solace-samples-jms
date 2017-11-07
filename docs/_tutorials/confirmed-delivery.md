---
layout: tutorials
title: Confirmed Delivery
summary: Learn how to confirm that your messages are received by Solace Messaging.
icon: I_dev_confirm.svg
---

This tutorial builds on the basic concepts introduced in [Persistence with Queues]({{ site.baseurl }}/persistence-with-queues) tutorial and will show you how to properly process publisher acknowledgements. Once an acknowledgement for a message has been received and processed, you have confirmed your persistent messages have been properly accepted by Soalce messaging and therefore can be guaranteed of no message loss.  

## Persistent Publishing with JMS version 1.1

In the JMS version 1.1, when sending PERSISTENT messages, the JMS MessageProducer must not return from the blocking send() method until the message is fully acknowledged by Soalce messaging. This behavior is mandated by the specification. Therefore applications sending persistent messages using JMS version 1.1 are guaranteed that the messages is accepted by Solace messaging by the time the MessageProducer.send() returns. No extra publisher acknowledgement handling is required or possible using the JMS API.

This restriction of the JMS 1.1 specification does mean that PERSISTENT message producers are forced to block on each message until it is fully guaranteed by the messaging system. This can lead to performance bottlenecks on publish. Applications can work around this by using JMS Session based transactions and committing the transaction only after several messages are sent to the messaging system.

Refer to the [JMS specification](http://download.oracle.com/otndocs/jcp/7195-jms-1.1-fr-spec-oth-JSpec/){:target="_blank"} for further details on this subject.

## Summarizing

For a JMS version 1.1 applications there is nothing further they must do to confirm message delivery with Solace messaging. This is handled by the API by making the send call blocking.

If you have any further questions ask the [Solace community]({{ site.links-community }}){:target="_top"}.

