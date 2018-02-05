[![Build Status](https://travis-ci.org/SolaceSamples/solace-samples-jms.svg?branch=master)](https://travis-ci.org/SolaceSamples/solace-samples-jms)

# Getting Started Examples
## Solace JMS API

The "Getting Started" tutorials will get you up to speed and sending messages with Solace technology as quickly as possible. There are three ways you can get started:

- Follow [these instructions](https://cloud.solace.com/create-messaging-service/) to quickly spin up a cloud-based Solace messaging service for your applications.
- Follow [these instructions](https://docs.solace.com/Solace-VMR-Set-Up/Setting-Up-VMRs.htm) to start the Solace VMR in leading Clouds, Container Platforms or Hypervisors. The tutorials outline where to download and how to install the Solace VMR.
- If your company has Solace message routers deployed, contact your middleware team to obtain the host name or IP address of a Solace message router to test against, a username and password to access it, and a VPN in which you can produce and consume messages.

## Contents

This repository contains code and matching tutorial walk throughs for basic Solace messaging patterns. For a nice introduction to the Solace API and associated tutorials, check out the [Getting Started Home Page](https://solacesamples.github.io/solace-samples-jms/).

See the individual tutorials for details:

- [Publish/Subscribe](https://solacesamples.github.io/solace-samples-jms/publish-subscribe): Learn how to set up pub/sub messaging on a Solace VMR.
- [Persistence](https://solacesamples.github.io/solace-samples-jms/persistence-with-queues): Learn how to set up persistence for guaranteed delivery.
- [Request/Reply](https://solacesamples.github.io/solace-samples-jms/request-reply): Learn how to set up request/reply messaging.
- [Confirmed Delivery](https://solacesamples.github.io/solace-samples-jms/confirmed-delivery): Learn how to confirm that your messages are received by a Solace message router.
- [Topic to Queue Mapping](https://solacesamples.github.io/solace-samples-jms/topic-to-queue-mapping): Learn how to map existing topics to Solace queues.
- [Obtaining JMS objects using JNDI](https://solacesamples.github.io/solace-samples-jms/using-jndi): Learn how to use JNDI as a way to create JMS objects.

## Prerequisites

This tutorial requires the Solace JMS API library. Download the JMS API library to your computer from [here](http://dev.solace.com/downloads/).

## Build the Samples

Just clone and build. For example:

  1. clone this GitHub repository
  1. `./gradlew assemble`

## Running the Samples

To try individual samples, build the project from source and then run samples like the following:

    ./build/staged/bin/topicPublisher <msg_backbone_ip:port>

The individual tutorials linked above provide full details which can walk you through the samples, what they do, and how to correctly run them to explore Solace messaging.

## Exploring the Samples

### Setting up your preferred IDE

Using a modern Java IDE provides cool productivity features like auto-completion, on-the-fly compilation, assisted refactoring and debugging which can be useful when you're exploring the samples and even modifying the samples. Follow the steps below for your preferred IDE.

#### Using Eclipse

To generate Eclipse metadata (.classpath and .project files), do the following:

    ./gradlew eclipse

Once complete, you may then import the projects into Eclipse as usual:

 *File -> Import -> Existing projects into workspace*

Browse to the *'solace-samples-jms'* root directory. All projects should import
free of errors.

#### Using IntelliJ IDEA

To generate IDEA metadata (.iml and .ipr files), do the following:

    ./gradlew idea

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Authors

See the list of [contributors](https://github.com/SolaceSamples/solace-samples-jms/contributors) who participated in this project.

## License

This project is licensed under the Apache License, Version 2.0. - See the [LICENSE](LICENSE) file for details.

## Resources

For more information try these resources:

- The Solace Developer Portal website at: http://dev.solace.com
- Get a better understanding of [Solace technology](http://dev.solace.com/tech/).
- Check out the [Solace blog](http://dev.solace.com/blog/) for other interesting discussions around Solace technology
- Ask the [Solace community.](http://dev.solace.com/community/)
