package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.util.Token;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;

public class CommandClass {
    private final static int BUFFER_SIZE = 1024;
    //private final Function<ByteBuffer, ReadProcessStatus> fillBuffer;
    //private final Function<ByteBuffer, WriteProcessStatus> process;

    private final byte opcode;
    private final List<Token<?>> tokens;
    private int currentToken = 0;
    private ReadProcessStatus readStatus  = ReadProcessStatus.REFILL;
    private WriteProcessStatus writeStatus = WriteProcessStatus.REFILL;
    private Status status = Status.WAIT_CMD;

    private ByteBuffer internalBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    enum ReadProcessStatus {DONE, REFILL, ERROR};
    enum WriteProcessStatus {DONE, REFILL, ERROR};
    enum Status {DONE, WAIT_CMD, ERROR};

    private byte getOpCode(){
        return opcode;
    }

    private CommandClass(CommandBuilder builder){
        /*
        this.fillBuffer = builder.fillBuffer;
        this.process = builder.process;
        */
        this.opcode = builder.opcode;
        this.tokens = List.copyOf(builder.tokens); // copy defensive;
    }

    public static CommandBuilder Builder(){
        return new CommandBuilder();
    }

    public static class CommandBuilder{
        private Function<ByteBuffer, ReadProcessStatus> fillBuffer;
        private Function<ByteBuffer, WriteProcessStatus> process;
        private byte opcode;
        private final List<Token<?>> tokens = new ArrayList<>();


        public CommandBuilder setFillBuffer(Function<ByteBuffer, ReadProcessStatus> function){
            this.fillBuffer = function;
            return this;
        }

        public CommandBuilder setProcess(Function<ByteBuffer, WriteProcessStatus> function){
            this.process = function;
            return this;
        }

        public CommandBuilder setOpcode(byte opcode){
            this.opcode = opcode;
            return this;
        }

        public CommandBuilder setToken(Token<?>... tokens){
            this.tokens.addAll(Arrays.asList(tokens));
            return this;
        }


        public CommandClass build(){
            Objects.requireNonNull(fillBuffer);
            Objects.requireNonNull(process);
            return new CommandClass(this);
        }
    }
}