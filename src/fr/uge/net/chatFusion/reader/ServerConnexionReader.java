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

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        //System.out.println("READER  : state == " + state);
        if (state == State.WAITING_NAME) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    //System.out.println("string reader : state = REFILL ");
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    //System.out.println("string reader : state = ERROR ");
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    //System.out.println("string reader : state = DONE ");
                    name = stringReader.get();
                    stringReader.reset();
                    state = State.WAITING_SOCKET_ADDRESS;
                }
            }
        }
        if (state == State.WAITING_SOCKET_ADDRESS) {
            switch (socketAddressTokenReader.process(bb)) {
                case REFILL -> {
                    //System.out.println("socketAddressTokenReader : state = REFILL");
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    //System.out.println("socketAddressTokenReader : state = ERROR");
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    //System.out.println("socketAddressTokenReader : state = DONE");
                    socketAddressToken = socketAddressTokenReader.get();
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public ServerConnexion get() {
        System.out.println("State (get) == " + state);
        if (state != State.DONE) {
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

    private enum State {
        DONE,
        WAITING_NAME,
        WAITING_SOCKET_ADDRESS,
        ERROR
    }
}
