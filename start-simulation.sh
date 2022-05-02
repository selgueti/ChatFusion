#!/bin/bash

###############################################################################################
#                                                                                             #
# Launch ServerFusionManager on localhost:8888                                                #
#                                                                                             #
# Launch 4 Servers on localhost:7777, localhost:7778, localhost:7779, localhost:7780          #
#                                                                                             #
# Connect 4 clients (1 client / server)                                                       #
#                                                                                             #
###############################################################################################


# ServerFusionManager
gnome-terminal -- java -jar server-fusion-manager/build/libs/ServerFusionManager-0.1.0.jar 8888

# ServerChatFusion
gnome-terminal -- java -jar server/build/libs/ServerChatFusion-0.1.0.jar Server1 7777 localhost 8888

gnome-terminal -- java -jar server/build/libs/ServerChatFusion-0.1.0.jar Server2 7778 localhost 8888

gnome-terminal -- java -jar server/build/libs/ServerChatFusion-0.1.0.jar Server3 7779 localhost 8888

gnome-terminal -- java -jar server/build/libs/ServerChatFusion-0.1.0.jar Server4 7780 localhost 8888

# ClientChatFusion
gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientA localhost 7777 /dev/null

gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientB localhost 7778 /dev/null

gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientC localhost 7779 /dev/null

gnome-terminal -- java -jar client/build/libs/ClientChatFusion-0.1.0.jar clientD localhost 7780 /dev/null
