# Java gRPC chat
## Description
This project features a simple distributed chat application using gRPC.
## Build
To build either execute ./build.sh or run the following commands:
```
gradle clean build
```
* gradle can be launched from gradle wrapper (gradlew) based on system you on

build.sh will also copy the generated jar files to the root directory of the project.
## Run
To run node execute ./run.sh or run on the following commands:
```
java -jar build/libs/chat-1.0-SNAPSHOT.jar <own ip> <port> <name>
```
or
```
java -jar gRPC_chat_sem.jar <own ip> <port> <name>
```
## Usage
All commands are available after typing /help in the chat.
