package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.ServerConnexion;

import java.nio.ByteBuffer;

public class ServerConnexionReader implements Reader<ServerConnexion> {
    private final StringReader stringReader = new StringReader();
    private String name;
    private State state = State.WAITING;
    private enum State{
        DONE,
        WAITING,
        ERROR
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        switch(state){
            case ERROR, DONE -> {
                throw new IllegalStateException();
            }
            case WAITING -> {
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
        return new ServerConnexion(name);
    }

    @Override
    public void reset() {
        state = State.WAITING;
        stringReader.reset();
    }
}
