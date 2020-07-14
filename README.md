# Membrane

In-memory key-value store built on top of [Laminar](https://github.com/jmdisher/Laminar).

This project exists as an example of how Laminar can be used but also as an example of how effective a key-value store based on making the data element within a struct the first-class storage element can be, as opposed to the traditional approach of storing large data structure in a key-value store as the first-class storage element (often requiring queries which can then parse this structure).

## How to build

Membrane is a Maven project so can be built and tested using the top-level command:

```
mvn clean install
```

## How to use

Once built, the final jar can be run with the client host/IP and port of a Laminar node (in this example, a node is configured to listen for clients on `127.0.0.1:8000`):

```
java -jar ./rest-server/target/rest-server-1.0-SNAPSHOT-jar-with-dependencies.jar --hostname 127.0.0.1 --port 8000
```

Now, interaction is done via REST (note that field definition uses multi-part POST).

For example:

```
# Define the "name" of a user as a String with no AVM program.
curl -XPOST "localhost:8080/name" -F "type=String" -F "code=" -F "arguments="
>name

# Define the "employee_number" of a user as an integer with no AVM program.
curl -XPOST "localhost:8080/employee_number" -F "type=integer" -F "code=" -F "arguments="
>employee_number

# Set the employee_number for "user1" and "user2".
# (note that "integer" field means a 4-byte big-endian binary value).
printf "\x00\x00\x00\x01" > number.temp && curl -XPUT "localhost:8080/employee_number/user1" --data-binary "@number.temp
>Received 4 bytes
printf "\x00\x00\x00\x02" > number.temp && curl -XPUT "localhost:8080/employee_number/user2" --data-binary "@number.temp
>Received 4 bytes

# Set names for these users.
curl -XPUT "localhost:8080/name/user1" -d "User 1 name"
>Received 11 bytes
curl -XPUT "localhost:8080/name/user2" -d "User 2 name"
>Received 11 bytes

# Read the users.
curl -XGET "localhost:8080/user1"
>{
>  "name": "User 1 name",
>  "employee_number": 1
>}
curl -XGET "localhost:8080/user2"
>{
>  "name": "User 2 name",
>  "employee_number": 2
>}
```

The multi-part post is used for defining fields as it is easier to send binary files that way, such as the "code" and "arguments" parameters.  An AVM JAR can be sent as the "code" in order to create a field which is backed by a programmable topic (see `test_avm.sh` or `MembraneRestTest.java` for more details on this).

Membrane is also about demonstrating how multiple key-value stores can use partially-overlapping data in the same backing Laminar cluster.

Considering the previous example, we can start another Membrane instance against the same cluster (in this example, Membrane was just restarted in order to clear its sense of what fields are in a document so it knows nothing about the cluster's contents):

```
# We rely on the employee_number but we want to allow an existing topic, if it is there (otherwise it will fail, since it was already created).
curl -XPOST "localhost:8080/employee_number" -F "type=integer" -F "code=" -F "arguments=" -F "allowExisting="
>employee_number

# We also want to define a nickname.
curl -XPOST "localhost:8080/nickname" -F "type=String" -F "code=" -F "arguments="
>nickname

# Set the nickname for our users, including one which is not an employee.
curl -XPUT "localhost:8080/nickname/user1" -d "Original user"
>Received 13 bytes
curl -XPUT "localhost:8080/nickname/user2" -d "Improved user"
>Received 13 bytes
curl -XPUT "localhost:8080/nickname/user3" -d "New and improved user"
>Received 21 bytes

# We can then view these documents, which will include the employee_number from the cluster, even though this Membrane instance never set it.
curl -XGET "localhost:8080/user1"
>{
>  "nickname": "Original user",
>  "employee_number": 1
>}
curl -XGET "localhost:8080/user2"
>{
>  "nickname": "Improved user",
>  "employee_number": 2
>}
curl -XGET "localhost:8080/user3"
>{
>  "nickname": "New and improved user"
>}
```

This shows how ad-hoc projections of topic data can be created to address different needs for different applications, instead of relying on something like a monolithic "user record".

