java-jscrambler
===============

1. Get your API credentials at https://jscrambler.com/en/account/api_access

2. Copy the pre-defined configuration file that best suite your needs and add
   there your API credentials and files list.

3. Run the client

   CLI
   ---
   > java jscrambler-client.jar config.json

   OR

   API
   ---

   > JScramblerFacade.process("config.json");


Requirements
------------

JRE 1.6 or above (http://www.oracle.com/technetwork/java/javase/downloads/index.html)
Apache HttpComponents 4.1.2 or above (http://hc.apache.org/downloads.cgi)
JSON in Java (http://json.org/java/)


Configuration
-------------
Your JScrambler's project configuration is achieved through a JSON file with the following structure:
```json
{
  "filesSrc": ["index.js", "lib/**/*.js", "lib/*.js"],
  "filesDest": "dist/",
  "keys": {
    "accessKey": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
    "secretKey": "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
  },
  "params": {
    "rename_local": "%DEFAULT%",
    "whitespace": "%DEFAULT%",
    "literal_duplicates": "%DEFAULT%"
  }
}
```

### Options
#### filesSrc
Type: `Array`

An array of string values with paths to the source files of your project that you wish to send to the JScrambler services. It supports minimatch/glob.

#### filesDest
Type: `String`

A string value that is used to provide the destination of the JScrambler's output.


#### keys.accessKey
Type: `String`

A string value that is used to provide the JScrambler API with the access key.

#### keys.secretKey
Type: `String`

A string value that is used to sign requests to the JScrambler API.


#### host
Type: `String`

A string value that is used to provide the JScrambler's host.

#### port
Type: `Number`

A number value that is used to provide the JScrambler's port.

#### apiVersion
Type: `String`

A string value that is used to select the version of JScrambler.

#### deleteProject
Type: `Boolean`

If this is set to `true` then the project will be deleted from JScrambler after it has been downloaded.

#### params
Type: `Object`

You can find a list of all the possible parameters in [here](https://github.com/auditmark/node-jscrambler#jscrambler-options).
