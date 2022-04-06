package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.reader.*;
import fr.uge.net.chatFusion.util.EntryRouteTable;
import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static fr.uge.net.chatFusion.reader.Reader.ProcessStatus;

public class ServerFusionManager {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerFusionManager.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Thread console;
    private final StringController stringController = new StringController();
    private final Map<EntryRouteTable, Context> serversConnected = new HashMap<>();
    private final ArrayDeque<ByteBuffer> fusionAsks = new ArrayDeque<>();
    private boolean isCurrentlyOnFusion = false;
    private int nbTableAlreadyReceive = 0;


    public ServerFusionManager(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.console = new Thread(this::consoleRun);
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerFusionManager(Integer.parseInt(args[0])).launch();
    }

    private static void usage() {
        System.out.println("Usage : ServerFusionManager port");
    }

    private EntryRouteTable retrieveServerNameFromContext(Context context) {
        for (var servers : serversConnected.keySet()) {
            if (serversConnected.get(servers) == context) {
                return servers;
            }
        }
        throw new AssertionError("Context is not in serversConnected map");
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                            Console Thread + management of the user's instructions                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void consoleRun() {
        boolean closed = false;
        try {
            try (var scanner = new Scanner(System.in)) {
                while (!closed && scanner.hasNextLine()) {
                    var command = scanner.nextLine();
                    if (command.equalsIgnoreCase("SHUTDOWNNOW")) {
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
        switch (command.toUpperCase()) {
            case "INFO" -> processInstructionInfo();
            case "INFOCOMPLETE" -> processInstructionInfoComplete();
            case "SHUTDOWN" -> processInstructionShutdown();
            case "SHUTDOWNNOW" -> processInstructionShutdownNow();
            default -> System.out.println("Unknown command");
        }
    }

    private int nbServerActuallyConnected() {
        return selector.keys().stream().mapToInt(key -> {
            if (key.isValid() && key.channel() != serverSocketChannel) {
                return 1;
            } else {
                return 0;
            }
        }).sum();
    }

    private void processInstructionInfo() {
        System.out.println("Servers connected : " + nbServerActuallyConnected());
    }

    private void processInstructionInfoComplete() {
        System.out.println("============================================");
        System.out.println("Selector information (reality) :");
        processInstructionInfo();

        System.out.println("\nMaps information (suppose) :");
        System.out.print("servers connected map -> ");
        StringJoiner sj = new StringJoiner(", ", "{", "}");

        System.out.println("map size : " + serversConnected.size());
        serversConnected.keySet().forEach(key -> sj.add(key.name() + key.socketAddressToken().address() + ":" +key.socketAddressToken().port()));

        System.out.println(sj);
        System.out.println("============================================");
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


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                selection loop                                                  //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        console.start();
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
            logger.log(Level.INFO, "Connection closed with server due to IOException", e);
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
        selectionKey.attach(new Context(selectionKey, this));
    }

    private void silentlyClose(SelectionKey key) {
        var sc = (Channel) key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           sfm commands processing                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // TODO add method for sfm commands processing
    private ProcessStatus processInUnregistered(Context context) {
        // we attempt FUSION_REGISTER_SERVER(8)
        //System.out.println("UNREGISERED:" + context.currentCommand);
        //System.out.println("bufferIn SFM : " + context.bufferIn);
        switch (context.currentCommand) {
            case 8 -> {
                switch (context.fusionRegisterServerReader.process(context.bufferIn)) {
                    case ERROR -> {
                        //System.out.println("ERROR");
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        //System.out.println("REFILL");
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        //System.out.println("DONE !");
                        var fusionRegisterServer = context.fusionRegisterServerReader.get();
                        context.fusionRegisterServerReader.reset();

                        var nameS1 = fusionRegisterServer.name();
                        var socket1 = fusionRegisterServer.socketAddressToken();

                        var entry = serversConnected.keySet().stream().filter(key -> key.name().equals(nameS1)).findAny();
                        if(entry.isPresent()){
                            System.out.println("Server already registered");
                        }
                        else{
                            context.authenticationState = Context.AuthenticationState.REGISTERED;
                            serversConnected.put(new EntryRouteTable(nameS1, socket1), context);
                            System.out.println("New registered server : " + nameS1);

                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            default -> {
                System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInUnregistered");
                context.silentlyClose();
                return ProcessStatus.ERROR;
            }
        }
        return ProcessStatus.DONE;
    }

    private ProcessStatus processInRegistered(Context context) {
        switch (context.currentCommand) {
            case 9 -> {
                switch (context.fusionInitReader.process(context.bufferIn)){
                    case ERROR -> {return ProcessStatus.ERROR;}
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var fusionInit = context.fusionInitReader.get();
                        context.fusionInitReader.reset();
                    }
                }
            }
            case 12 -> {

            }

            default -> {
                System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInRegistered");
                context.silentlyClose();
                return ProcessStatus.ERROR;
            }
        }
        return ProcessStatus.DONE;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ServerFusionManager server; // we could also have Context as an instance class, which would naturally
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();

        // readers
        private final BytesReader opcodeReader = new BytesReader(1);
        private final FusionRegisterServerReader fusionRegisterServerReader = new FusionRegisterServerReader();
        private final FusionInitReader fusionInitReader = new FusionInitReader();
        private final FusionRouteTableSendReader fusionRouteTableSendReader = new FusionRouteTableSendReader();

        // give access to ServerFusionManager.this
        private boolean closed = false;
        private Writer writer = null;
        private ReadingState readingState = ReadingState.WAITING_OPCODE;
        private AuthenticationState authenticationState = AuthenticationState.UNREGISTERED;
        private byte currentCommand;

        private Context(SelectionKey key, ServerFusionManager server) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                switch (assureCurrentCommandSet()) {
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                    case REFILL -> {
                        return;
                    }
                    case DONE -> {
                        switch (authenticationState) {
                            case UNREGISTERED -> {
                                switch (server.processInUnregistered(this)) {
                                    case REFILL -> {
                                        //System.out.println("ProcessIN refill");
                                        return;
                                    }
                                       case ERROR -> {
                                           //System.out.println("ProcessIN error");
                                           silentlyClose();
                                           return;
                                    }
                                    case DONE -> {
                                        //System.out.println("ProcessIN done");
                                        continue;
                                        //return;
                                    }
                                }
                            }
                            case REGISTERED -> {
                                switch (server.processInRegistered(this)) {
                                    case REFILL -> {
                                        return;
                                    }
                                    case ERROR -> {
                                        silentlyClose();
                                        return;
                                    }
                                    case DONE -> {
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Add a text to the text queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - command to add to the command queue
         */
        private void queueCommand(ByteBuffer cmd) {
            queueCommand.addLast(cmd);
            processOut();
            updateInterestOps();
        }

        /**
         * Assure currentCommand is set
         */
        private ProcessStatus assureCurrentCommandSet() {
            if (readingState == ReadingState.WAITING_OPCODE) {
                switch (opcodeReader.process(bufferIn)) {
                    case ERROR -> {
                        return Reader.ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return Reader.ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        currentCommand = opcodeReader.get()[0];
                        opcodeReader.reset();
                        readingState = ReadingState.PROCESS_IN;
                    }
                }
            }
            return Reader.ProcessStatus.DONE;
        }

        /**
         * Try to fill bufferOut from the command queue
         */
        private void processOut() {
            while (!queueCommand.isEmpty()) {
                if (writer == null) {
                    var command = queueCommand.peekFirst();
                    writer = new Writer(command);
                }
                writer.fillBuffer(bufferOut);
                if (writer.isDone()) {
                    queueCommand.removeFirst();
                    writer = null;
                }
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
            server.serversConnected.remove(server.retrieveServerNameFromContext(this));
            System.out.println("remove server from map");
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

        enum ReadingState {
            WAITING_OPCODE,
            PROCESS_IN,
        }

        enum AuthenticationState {
            UNREGISTERED, REGISTERED
        }
    }
}