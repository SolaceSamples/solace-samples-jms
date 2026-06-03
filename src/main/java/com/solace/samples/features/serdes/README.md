This package will contain Java samples that demonstrate how to Serialize and Deserialize Messages with the Solace JMS API.

### Solace Schema Registry
For information about how to deploy and configure the Solace Schema Registry, please refer to our documentation here:
https://docs.solace.com/Schema-Registry/schema-registry-overview.htm

### Uploading Schemas

To upload a schema in the Solace Schema Registry, follow these steps:

1. Begin by logging into an account with write access and click on the "Create Artifact" button.

2. Leave the Group Id field empty.

   ### JSON Schema
    - **Artifact Id**: Use a unique identifier for each schema:
        - For `user.json`, use `solace/samples/json`
        - **Type**: Select `JSON Schema`.

>   **Note:** Each schema must be uploaded separately with its own unique Artifact Id to avoid conflicts.

After setting the Artifact ID and Type, follow these steps:

3. Click the "Next" button to proceed.

4. You can skip the Artifact Metadata section as it's not required. Simply press "Next" to continue.

5. On the Version Content Page, leave the version set to auto, or if preferred, enter a specific value of your choice.

### For JSON Schema:
6. On the Version Content Page, upload the appropriate schema file from the `src/main/resources/json-schema/` directory:
    - When using Artifact Id `solace/samples/json`, upload `user.json`

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
