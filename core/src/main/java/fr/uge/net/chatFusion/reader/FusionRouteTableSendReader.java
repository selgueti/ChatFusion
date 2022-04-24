package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionRouteTableSend;
import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class FusionRouteTableSendReader implements Reader<FusionRouteTableSend> {

    private final StringReader serverNameReader = new StringReader();
    private final IntReader nbMembersReader = new IntReader();
    private final SocketAddressTokenReader socketAddressTokenReader = new SocketAddressTokenReader();
    private State state = State.WAITING_NB_MEMBERS;
    private Map<String, SocketAddressToken> routes = new HashMap<>();
    private int nbMembers;
    private String currentServName;
    private int nbMembersRegistered = 0;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            System.out.println("STATE ===== " + state);
            throw new IllegalStateException();
        }
        //System.out.println("BB = " + bb);
        if (state == State.WAITING_NB_MEMBERS) {
            switch (nbMembersReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    nbMembers = nbMembersReader.get();
                    if(nbMembers < 0){
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    nbMembersReader.reset();
                    state = State.WAITING_SERVER_NAME;
                }
            }
        }

        while(nbMembersRegistered != nbMembers){
            if (state == State.WAITING_SERVER_NAME) {
                switch (serverNameReader.process(bb)) {
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        currentServName = serverNameReader.get();
                        serverNameReader.reset();
                        state = State.WAITING_SOCKET_ADDRESS;
                    }
                }
            }

            if (state == State.WAITING_SOCKET_ADDRESS) {
                switch (socketAddressTokenReader.process(bb)) {
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        var currentSocketAddress = socketAddressTokenReader.get();
                        socketAddressTokenReader.reset();

                        // registered to the map
                        routes.put(currentServName, currentSocketAddress);
                        nbMembersRegistered++;
                        state = State.WAITING_SERVER_NAME;
                    }
                }
            }
        }

        //System.out.println("nbMembersRegistered : " + nbMembersRegistered + " == nbMembers : " + nbMembers);
        state = State.DONE;

        return ProcessStatus.DONE;
    }

    @Override
    public FusionRouteTableSend get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FusionRouteTableSend(nbMembers, routes);
    }

    @Override
    public void reset() {
        serverNameReader.reset();
        nbMembersReader.reset();
        socketAddressTokenReader.reset();
        state = State.WAITING_NB_MEMBERS;
        routes = new HashMap<>();
        nbMembers = 0;
        currentServName = null;
        nbMembersRegistered = 0;
    }

    private enum State {
        DONE, ERROR, WAITING_NB_MEMBERS, WAITING_SERVER_NAME, WAITING_SOCKET_ADDRESS
    }
}
