package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionInit;
import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.nio.ByteBuffer;

public class FusionInitReader implements Reader<FusionInit> {
    private final SocketAddressTokenReader socketReader = new SocketAddressTokenReader();
    private SocketAddressToken socketAddress;
    private State state = State.WAITING_SOCKET;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        switch (state) {
            case ERROR, DONE -> {
                throw new IllegalStateException();
            }
            case WAITING_SOCKET -> {
                switch (socketReader.process(bb)) {
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        socketAddress = socketReader.get();
                        socketReader.reset();
                        state = State.DONE;
                        return ProcessStatus.DONE;
                    }
                }

            }
        }
        throw new AssertionError();
    }

    @Override
    public FusionInit get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new FusionInit(socketAddress);
    }

    @Override
    public void reset() {
        socketReader.reset();
        state = State.WAITING_SOCKET;
        socketAddress = null;
    }

    private enum State {
        DONE,
        WAITING_SOCKET,
        ERROR
    }
}
