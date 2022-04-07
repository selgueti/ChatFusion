package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.MessagePublicTransmit;

import java.nio.ByteBuffer;

public class MessagePublicTransmitReader implements Reader<MessagePublicTransmit> {
    private static final StringReader stringReader = new StringReader();
    private String server;
    private String login;
    private String msg;
    private State state = State.WAITING_SERVER;

    private enum State{
        DONE,
        ERROR,
        WAITING_SERVER,
        WAITING_LOGIN,
        WAITING_MSG
    }
    @Override
    public ProcessStatus process(ByteBuffer bb) {
            if(state == State.DONE || state == State.ERROR){
                throw new IllegalStateException();
            }
            if(state == State.WAITING_SERVER){
                switch(stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        server = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_LOGIN;
                    }
                }
            }
            if(state == State.WAITING_LOGIN){
                switch(stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        login = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_MSG;
                    }
                }
            }
            if(state == State.WAITING_MSG){
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        msg = stringReader.get();
                        stringReader.reset();
                        state = State.DONE;
                        return ProcessStatus.DONE;
                    }
                }
            }
        throw new AssertionError();
    }

    @Override
    public MessagePublicTransmit get() {
        if(state != State.DONE){
            throw new IllegalStateException();
        }
        return new MessagePublicTransmit(server, login, msg);
    }

    @Override
    public void reset() {
        stringReader.reset();
        server = null;
        login = null;
        msg = null;
    }
}
