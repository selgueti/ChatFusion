package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.command.MessagePublicSend;
import fr.uge.net.chatFusion.util.StringController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerChatFusion {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final InetSocketAddress sfmAddress;
    private final Selector selector;
    private final String name;
    private final Thread console;
    private final StringController stringController = new StringController();
    //private final Map<Byte, OpCodeEntry> commandMap = new HashMap<>();

    private final Map<Byte, Consumer<ByteBuffer>> readers = new HashMap<>();
    //private final PublicMessageReader publicMessageReader = new PublicMessageReader();

    /*private void setOpCodeEntries(){
        readers.put((byte)4, publicMessageReader::process);
    }*/

    public ServerChatFusion(String name, int port, InetSocketAddress sfmAddress) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.sfmAddress = sfmAddress;
        this.name = name;
        this.console = new Thread(this::consoleRun);
        //setOpCodeEntries();
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ServerChatFusion(args[0], Integer.parseInt(args[1]), new InetSocketAddress(args[2], Integer.parseInt(args[3]))).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerChatFusion name port address_sfm port_sfm");
    }

    @Override
    public String toString() {
        return "ServerChatFusion{" +
                "name=" + name +
                ", sfmAddress=" + sfmAddress +
                '}';
    }

    private void consoleRun() {
        boolean closed = false;
        try {
            try (var scanner = new Scanner(System.in)) {
                while (!closed && scanner.hasNextLine()) {
                    var command = scanner.nextLine();
                    if (command.equals("SHUTDOWNNOW")) {
                        closed = true;
                        scanner.close();
                    }
                    sendInstruction(command);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
        }
    }

    /**
     * Send instructions to the selector via messageController and wake it up
     *
     * @param instruction - user input
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendInstruction(String instruction) throws InterruptedException {
        stringController.add(instruction, selector);
        //selector.wakeup();
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    /**
     * Processes the command from the messageController
     */
    private void processInstructions() throws IOException {
        while (stringController.hasString()) {
            treatInstruction(stringController.poll());
        }
    }

    private void treatInstruction(String command) throws IOException {
        switch (command) {
            case "INFO" -> processInstructionInfo();
            case "SHUTDOWN" -> processInstructionShutdown();
            case "SHUTDOWNNOW" -> processInstructionShutdownNow();
            default -> System.out.println("Unknown command");
        }
    }

    private int nbClientActuallyConnected() {
        return selector.keys().stream().mapToInt(key -> {
            if (key.isValid() && key.channel() != serverSocketChannel) {
                return 1;
            } else {
                return 0;
            }
        }).sum();
    }

    private void processInstructionInfo() {
        logger.info("There are currently " + nbClientActuallyConnected() + " clients connected to the server");
    }

    private void processInstructionShutdown() {
        logger.info("shutdown...");
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            logger.severe("IOESHUTDOWN" + e.getCause());
        }
    }

    private void processInstructionShutdownNow() throws IOException {
        logger.info("shutdown now...");
        for (SelectionKey key : selector.keys()) {
            if (key.channel() != serverSocketChannel && key.isValid()) {
                key.channel().close();
            }
        }
        console.interrupt();
        Thread.currentThread().interrupt();
    }


    public void launch() throws IOException {
        System.out.println(this);
        console.start();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // TODO connect to SFM and send the FUSION_REGISTER_SERVER(8) command

        while (!Thread.interrupted()) {
            Helpers.printKeys(selector); // for debug
            System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processInstructions();
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }
        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with client due to IOException", e);
            silentlyClose(key);
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        var sc = serverSocketChannel.accept();
        if (sc == null) {
            logger.info("Selector lied, no accept");
            return;
        }
        sc.configureBlocking(false);
        var selectionKey = sc.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(new Context(this, selectionKey));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }


    /**
     * Add a text to all connected clients queue
     *
     * @param cmd - text to add to all connected clients queue
     */
    /*private void broadcast(Command cmd) {
        Objects.requireNonNull(cmd);
        for (SelectionKey key : selector.keys()) {
            if (key.channel() == serverSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment(); // Safe Cast
            if(commandMap.containsKey(cmd.getOpcode())){
                commandMap.get(cmd.getOpcode()).handler().accept(cmd);
                PublicMessage pm = (PublicMessage) cmd;//safe cast beurk !
                context.queueCommand(new PublicMessage(pm.getLogin(), pm.getMsg()));
            }
        }
    }*/



    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<MessagePublicSend> queue = new ArrayDeque<>();
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        // give access to ServerChatInt.this
        private boolean closed = false;
        private State state = State.WAITING_OPCODE;
        private byte currentProcess;

        //private final PublicMessageReader pmr = new PublicMessageReader();


        private Context(ServerChatFusion server, SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        enum State {
            WAITING_OPCODE,
            PROCESS_IN
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                if(state == Context.State.WAITING_OPCODE && bufferIn.position() != 0){
                    currentProcess = bufferIn.get();
                    state = Context.State.PROCESS_IN;
                }
                if(state == Context.State.PROCESS_IN){
                    /*if(server.commandMap.containsKey(currentProcess)){
                        // Read the command
                        var readStatus = server.commandMap.get(currentProcess).command().readFrom(bufferIn);
                        switch (readStatus) {
                            case DONE:
                                var opCodeEntry = server.commandMap.get(currentProcess);

                                // applies the processing for this order
                                server.commandMap.get(opCodeEntry.command().getOpcode()).handler().accept(server.commandMap.get(opCodeEntry.command().getOpcode()).command());
                                server.readers.get(opCodeEntry.command().getOpcode()).accept();


                                opCodeEntry.command().reset(); // for next reading
                                state = Context.State.WAITING_OPCODE;
                                break;
                            case REFILL_INPUT:
                                return;

                            case ERROR:
                                //silentlyClose(); // not for a client, need to think about this
                                return;
                        }
                    }*/
                }
            }
        }

        /**
         * Add a text to the text queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - command to add to the command queue
         */
        /*public void queueCommand(Command cmd) {
            queue.addLast(cmd);
            processOut();
            updateInterestOps();
        }*/

        /**
         * Try to fill bufferOut from the command queue
         */
        private void processOut() {
            while (!queue.isEmpty()) {
                var command = queue.peekFirst();
                /*command.writeIn(bufferOut);
                if (command.isTotallyWritten()) {
                    queue.removeFirst();
                }*/
            }
        }

        /**
         * Update the interestOps of the key looking only at values of the boolean
         * closed and of both ByteBuffers.
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * updateInterestOps and after the call. Also, it is assumed that process has
         * been be called just before updateInterestOps.
         */
        private void updateInterestOps() {
            var interestOps = 0;
            if (!key.isValid()) {
                return;
            }
            if (!closed && bufferIn.hasRemaining()) {
                interestOps |= SelectionKey.OP_READ;
            }
            if (bufferOut.position() != 0) {
                interestOps |= SelectionKey.OP_WRITE;
            }
            if (interestOps == 0) {
                silentlyClose();
                return;
            }
            key.interestOps(interestOps);
        }

        private void silentlyClose() {
            try {
                sc.close();
            } catch (IOException e) {
                // ignore exception
            }
        }

        /**
         * Performs the read action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doRead and after the call
         *
         * @throws IOException - if some I/O error occurs
         */
        private void doRead() throws IOException {
            if (-1 == sc.read(bufferIn)) {
                closed = true;
            }
            processIn();
            updateInterestOps();
        }

        /**
         * Performs the write action on sc
         * <p>
         * The convention is that both buffers are in write-mode before the call to
         * doWrite and after the call
         *
         * @throws IOException - if some I/O error occurs
         */
        private void doWrite() throws IOException {
            bufferOut.flip();
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }
    }
}