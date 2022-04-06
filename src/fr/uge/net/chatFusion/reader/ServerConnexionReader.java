package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.ServerConnexion;
import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.nio.ByteBuffer;

public class ServerConnexionReader implements Reader<ServerConnexion> {
    private final StringReader stringReader = new StringReader();
    private final SocketAddressTokenReader socketAddressTokenReader = new SocketAddressTokenReader();
    private String name;
    private SocketAddressToken socketAddressToken;
    private State state = State.WAITING_NAME;
    private enum State{
        DONE,
        WAITING_NAME,
        WAITING_SOCKET_ADDRESS,
        ERROR
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        switch(state){
            case ERROR, DONE -> {
                throw new IllegalStateException();
            }
            case WAITING_NAME -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        name = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_SOCKET_ADDRESS;
                        //return ProcessStatus.REFILL;
                    }
                }
            }

            case WAITING_SOCKET_ADDRESS -> {
                switch (socketAddressTokenReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        socketAddressToken = socketAddressTokenReader.get();
                        socketAddressTokenReader.reset();
                        return ProcessStatus.DONE;
                    }
                }

            }
        }
        return ProcessStatus.ERROR;
    }

    @Override
    public ServerConnexion get() {
        if(state != State.DONE){
            throw new IllegalStateException();
        }
        return new ServerConnexion(name, socketAddressToken);
    }

    @Override
    public void reset() {
        state = State.WAITING_NAME;
        stringReader.reset();
        socketAddressTokenReader.reset();
        name = null;
        socketAddressToken = null;
    }
}
