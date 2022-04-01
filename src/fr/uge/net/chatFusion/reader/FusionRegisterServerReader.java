package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FusionRegisterServer;

import java.nio.ByteBuffer;

public class FusionRegisterServerReader implements Reader<FusionRegisterServer> {

    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_NAME;
    private String name;
    
    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.DONE || state == State.ERROR) {
            throw new IllegalStateException();
        }
        if (state == State.WAITING_NAME) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    name = stringReader.get();
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
        return new FusionRegisterServer(name);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAITING_NAME;
        name = null;
    }

    private enum State {
        DONE, WAITING_NAME, ERROR
    }
}
