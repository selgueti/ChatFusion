package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.SocketAddressToken;

import java.nio.ByteBuffer;

public class SocketAddressTokenReader implements Reader<SocketAddressToken> {
    private final BytesReader versionReader = new BytesReader(1);
    private final IntReader portReader = new IntReader();
    private BytesReader addressReader; // need to read version before initialize
    private State state = State.WAITING_VERSION;
    private byte version;
    private byte[] address;
    private int port;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_VERSION) {
            switch (versionReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    version = versionReader.get()[0];
                    if (version != 4 && version != 6) {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    state = State.WAITING_ADDRESS;
                    if (version == 4) {
                        addressReader = new BytesReader(4);
                    } else {
                        addressReader = new BytesReader(16);
                    }
                }
            }
        }

        if (state == State.WAITING_ADDRESS) {
            switch (addressReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    address = addressReader.get();
                    if ((address.length == 4 && version == 6) || (address.length == 16 && version == 4)) {
                        throw new AssertionError("bad initialize addressReader");
                    }
                    state = State.WAITING_PORT;
                }
            }
        }

        if (state == State.WAITING_PORT) {
            switch (portReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    port = portReader.get();
                    state = State.DONE;
                }
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public SocketAddressToken get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new SocketAddressToken(version, address, port);
    }

    @Override
    public void reset() {
        versionReader.reset();
        addressReader = null;
        portReader.reset();
        state = State.WAITING_VERSION;
        version = 0;
        address = null;
        port = 0;
    }

    private enum State {
        DONE, WAITING_VERSION, WAITING_ADDRESS, WAITING_PORT, ERROR
    }
}
