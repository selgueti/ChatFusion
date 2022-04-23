package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionRegisterServer;
import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.nio.ByteBuffer;

public class FusionRegisterServerReader implements Reader<FusionRegisterServer> {

    private final StringReader stringReader = new StringReader();
    private final SocketAddressTokenReader socketAddressTokenReader = new SocketAddressTokenReader();
    private State state = State.WAITING_NAME;
    private String name;
    private SocketAddressToken socketAddressToken;
    
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
    public FusionRegisterServer get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        //System.out.println(new FusionRegisterServer(name, socketAddressToken));
        //System.out.println("get FusionRegisterServerReader");
        return new FusionRegisterServer(name, socketAddressToken);
    }

    @Override
    public void reset() {
        stringReader.reset();
        socketAddressTokenReader.reset();
        state = State.WAITING_NAME;
        name = null;
        socketAddressToken = null;
    }

    private enum State {
        DONE, WAITING_NAME, WAITING_SOCKET_ADDRESS, ERROR
    }
}
