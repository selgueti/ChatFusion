package fr.uge.net.chatFusion.reader;

import fr.uge.net.chatFusion.command.FilePrivate;

import java.nio.ByteBuffer;

public class FilePrivateReader implements Reader<FilePrivate> {
    private final StringReader stringReader = new StringReader();
    private final IntReader intReader = new IntReader();
    private BytesReader bytesReader;
    private String serverSrc;
    private String loginSrc;
    private String serverDst;
    private String loginDst;
    private String fileName;
    private int nbBlocks;
    private int blockSize;
    private byte[] bytes;


    private State state = State.WAITING_SERVER_SRC;

    private enum State{
        DONE,
        WAITING_SERVER_SRC,
        WAITING_LOGIN_SRC,
        WAITING_SERVER_DST,
        WAITING_LOGIN_DST,
        WAITING_FILE_NAME,
        WAITING_NB_BLOCKS,
        WAITING_SIZE,
        WAITING_CONTENT,
        ERROR
    }

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        switch(state){
            case DONE, ERROR -> throw new IllegalStateException();
            case WAITING_SERVER_SRC -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        serverSrc = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_LOGIN_SRC;
                    }
                }
            }
            case WAITING_LOGIN_SRC -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        loginSrc = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_SERVER_DST;
                    }
                }
            }
            case WAITING_SERVER_DST -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        serverDst = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_LOGIN_DST;
                    }
                }
            }
            case WAITING_LOGIN_DST -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = state.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        loginDst = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_FILE_NAME;
                    }
                }
            }
            case WAITING_FILE_NAME -> {
                switch (stringReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        fileName = stringReader.get();
                        stringReader.reset();
                        state = State.WAITING_NB_BLOCKS;
                    }
                }
            }
            case WAITING_NB_BLOCKS -> {
                switch (intReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        nbBlocks= intReader.get();
                        intReader.reset();
                        state = State.WAITING_SIZE;
                    }
                }
            }
            case WAITING_SIZE -> {
                switch (intReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        blockSize = intReader.get();
                        intReader.reset();
                        bytesReader = new BytesReader(blockSize);
                        state = State.WAITING_CONTENT;
                    }
                }
            }
            case WAITING_CONTENT -> {
                switch (bytesReader.process(bb)){
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case ERROR -> {
                        state = State.ERROR;
                        return ProcessStatus.ERROR;
                    }
                    case DONE -> {
                        bytes = bytesReader.get();
                        bytesReader.reset();
                        state = State.DONE;
                        return ProcessStatus.DONE;
                    }
                }
            }

        }
        throw new AssertionError("should not be able to access this, FIX ASAP !!!!!!");
    }

    @Override
    public FilePrivate get() {
        if (state != State.DONE){
            throw new IllegalStateException();
        }
        return new FilePrivate(serverSrc, loginSrc, serverDst, loginDst, fileName, nbBlocks, blockSize, bytes);
    }

    @Override
    public void reset() {
        stringReader.reset();
        intReader.reset();
        state = State.WAITING_SERVER_SRC;
    }
}
