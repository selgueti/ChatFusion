# ChatFusion

## Build
```
./gradlew build
```

## How to run
ServerFusionManager :
```
java -jar server-fusion-manager/build/libs/ServerFusionManager-0.1.0.jar <sfm-port>
```

Server :
```
java -jar server/build/libs/ServerChatFusion-0.1.0.jar <server-name> <server-port> <sfm-adress> <sfm-port>
```

Client :
```
java -jar client/build/libs/ClientChatFusion-0.1.0.jar <username> <server-adress> <server-port> <folder>
```

## Documentation
```
./gradlew javadoc
```
Then see : 
 - `core/build/docs/javadoc/index.html`
 - `server/build/docs/javadoc/index.html`
 - `client/build/docs/javadoc/index.html`
 - `server-fusion-manager/build/docs/javadoc/index.html`

## Testing
```
./gradlew test
```
Then see :
 - `core/build/reports/tests/test/index.html`