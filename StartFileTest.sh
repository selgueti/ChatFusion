#!/bin/bash

###############################################################################################
#                                                                                             #
# Launch ServerFusionManager on localhost:8888                                                #
#                                                                                             #
# Launch 1 Servers on localhost:7777                                                          #
#                                                                                             #
# Connect 2 clients (1 client / server)                                                       #
#                                                                                             #
###############################################################################################


# ServerChatFusion
gnome-terminal -- java -jar server/build/libs/ServerChatFusion-0.1.0.jar Server1 7777 localhost 8888

# ClientChatFusion
mkdir -p /tmp/client1
gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientA localhost 7777 /tmp/client1

mkdir -p /tmp/client2
gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientB localhost 7777 /tmp/client2
