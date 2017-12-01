
## Obtaining the Solace API

This tutorial depends on you having the Solace Messaging API for JMS. Here are a few easy ways to get the JMS API. The instructions in the [Building](#building) section assume you're using Gradle and pulling the jars from maven central. If your environment differs then adjust the build instructions appropriately.

### Get the API: Using Gradle

```
compile("com.solacesystems:sol-jms:10.2.1")
```

### Get the API: Using Maven

```
<dependency>
  <groupId>com.solacesystems</groupId>
  <artifactId>sol-jms</artifactId>
  <version>10.2.1</version>
</dependency>
```

### Get the API: Using the Solace Developer Portal

The Java API library can be [downloaded here]({{ site.links-downloads }}){:target="_top"}. The JMS API is distributed as a zip file containing the required jars, API documentation, and examples. 
