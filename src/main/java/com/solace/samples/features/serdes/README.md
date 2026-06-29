This package will contain Java samples that demonstrate how to Serialize and Deserialize Messages with the Solace JMS API.

### Solace Schema Registry
For information about how to deploy and configure the Solace Schema Registry, please refer to our documentation here:
https://docs.solace.com/Schema-Registry/schema-registry-overview.htm

### Uploading Schemas

To upload a schema in the Solace Schema Registry, follow these steps:

1. Begin by logging into an account with write access and click on the "Create Artifact" button.

2. Leave the Group Id field empty.

   ### Avro Schema
    - **Artifact Id**: Use a unique identifier for each schema:
        - For `user.avsc`, use `solace/samples/avro`
        - For `create-user.avsc`, use `solace/samples/create-user/avro`
        - For `create-user-response.avsc`, use `solace/samples/create-user-response/avro`
        - **Type**: Select `Avro Schema`.

   ### JSON Schema
    - **Artifact Id**: Use a unique identifier for each schema:
        - For `user.json`, use `solace/samples/json`
        - For `create-user.json`, use `solace/samples/create-user/json`
        - For `create-user-response.json`, use `solace/samples/create-user-response/json`
        - **Type**: Select `JSON Schema`.

>   **Note:** Each schema must be uploaded separately with its own unique Artifact Id to avoid conflicts.

After setting the Artifact ID and Type, follow these steps:

3. Click the "Next" button to proceed.

4. You can skip the Artifact Metadata section as it's not required. Simply press "Next" to continue.

5. On the Version Content Page, leave the version set to auto, or if preferred, enter a specific value of your choice.

6. On the Version Content Page, upload the appropriate schema file:
    - **Avro Schema**: from the `src/main/resources/avro-schema/` directory:
        - When using Artifact Id `solace/samples/avro`, upload `user.avsc`
        - When using Artifact Id `solace/samples/create-user/avro`, upload `create-user.avsc`
        - When using Artifact Id `solace/samples/create-user-response/avro`, upload `create-user-response.avsc`
    - **JSON Schema**: from the `src/main/resources/json-schema/` directory:
        - When using Artifact Id `solace/samples/json`, upload `user.json`
        - When using Artifact Id `solace/samples/create-user/json`, upload `create-user.json`
        - When using Artifact Id `solace/samples/create-user-response/json`, upload `create-user-response.json`

7. Click "Next" to move forward.

8. The Version Metadata is not necessary and can be skipped.

9. Finally, click the "Create" button to complete the process.

>   **Note:**  The registry URL, username, and password can be customized by setting environment variables.
If not set, the application will use default values.
To override the defaults, set the following environment variables before running the application:
The values shown below are the default settings. Modify these as needed for your specific registry configuration.
```shell
export REGISTRY_URL="http://localhost:8081/apis/registry/v3"
export REGISTRY_USERNAME="sr-readonly"
export REGISTRY_PASSWORD="roPassword"
```

For additional SERDES snippets see the [samples here](https://github.com/SolaceSamples/solace-samples-java-jcsmp/tree/master/src/main/java/com/solace/samples/jcsmp/snippets/serdes).

---

## Available Samples

Build the project first with `./gradlew assemble`, then run any sample using the generated scripts under `build/staged/bin/`. The default broker and schema registry connection values assume a local setup:

- Broker: `localhost:55555`, VPN: `default`, username: `default`
- Schema Registry: `http://localhost:8081/apis/registry/v3` (user: `sr-readonly`, password: `roPassword`)

Override the registry connection with environment variables before running if needed (see the [environment variable defaults](#solace-schema-registry) above).

### Avro Samples

| Sample | Description | Command |
|--------|-------------|---------|
| `HelloWorldJmsAvroSerde` | Publishes and subscribes to a single message using Avro serialization and deserialization in one sample. | `./build/staged/bin/HelloWorldJmsAvroSerde localhost:55555 default default` |
| `AvroSerializeProducer` | Serializes a `User` Avro generic record and publishes it to a topic. Pair with `AvroDeserializeConsumer`. | `./build/staged/bin/AvroSerializeProducer localhost:55555 default default` |
| `AvroSerializeProducerSpecificRecord` | Serializes a strongly-typed generated `User` class and publishes it to a topic. Pair with `AvroDeserializeConsumer` or `AvroDeserializeConsumerSpecificRecord`. | `./build/staged/bin/AvroSerializeProducerSpecificRecord localhost:55555 default default` |
| `AvroDeserializeConsumer` | Subscribes to a topic and deserializes received messages into an Avro `GenericRecord`. | `./build/staged/bin/AvroDeserializeConsumer localhost:55555 default default` |
| `AvroDeserializeConsumerSpecificRecord` | Subscribes to a topic and deserializes received messages into a strongly-typed generated `User` class. | `./build/staged/bin/AvroDeserializeConsumerSpecificRecord localhost:55555 default default` |
| `AvroSerdesReplier` | Request-Reply replier: receives a `CreateUser` request, deserializes it, builds a `CreateUserResponse`, and sends it back. Start before `AvroSerdesRequestor`. | `./build/staged/bin/AvroSerdesReplier localhost:55555 default default` |
| `AvroSerdesRequestor` | Request-Reply requestor: serializes a `CreateUser` request, publishes it, and deserializes the `CreateUserResponse` reply. | `./build/staged/bin/AvroSerdesRequestor localhost:55555 default default` |

### JSON Schema Samples

| Sample | Description | Command |
|--------|-------------|---------|
| `HelloWorldJmsJsonSchemaSerde` | Publishes and subscribes to a single message using JSON Schema serialization and deserialization in one sample. | `./build/staged/bin/HelloWorldJmsJsonSchemaSerde localhost:55555 default default` |
| `JsonSchemaSerializeProducer` | Serializes a `User` POJO and publishes it to a topic. Pair with a JSON Schema consumer. | `./build/staged/bin/JsonSchemaSerializeProducer localhost:55555 default default` |
| `JsonSchemaDeserializeConsumerToJsonNode` | Subscribes to a topic and deserializes received messages into a `JsonNode` for generic JSON handling. | `./build/staged/bin/JsonSchemaDeserializeConsumerToJsonNode localhost:55555 default default` |
| `JsonSchemaDeserializeConsumerToPojo` | Subscribes to a topic and deserializes received messages into a strongly-typed `User` POJO using the `customJavaType` property in the schema. | `./build/staged/bin/JsonSchemaDeserializeConsumerToPojo localhost:55555 default default` |
| `JsonSchemaSerdesReplier` | Request-Reply replier: receives a `CreateUser` request, deserializes it to a POJO, builds a `CreateUserResponse`, and sends it back. Start before `JsonSchemaSerdesRequestor`. | `./build/staged/bin/JsonSchemaSerdesReplier localhost:55555 default default` |
| `JsonSchemaSerdesRequestor` | Request-Reply requestor: serializes a `CreateUser` POJO, publishes it, and deserializes the `CreateUserResponse` reply. | `./build/staged/bin/JsonSchemaSerdesRequestor localhost:55555 default default` |

