package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.command.FusionRegisterServer;
import fr.uge.net.chatFusion.command.FusionRouteTableSend;
import fr.uge.net.chatFusion.command.LoginAccepted;
import fr.uge.net.chatFusion.command.SocketAddressToken;
import fr.uge.net.chatFusion.reader.*;
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

public class ServerChatFusion {
    private static final int BUFFER_SIZE = 1_024;
    private static final Logger logger = Logger.getLogger(ServerChatFusion.class.getName());
    private final ServerSocketChannel serverSocketChannel;
    private final InetSocketAddress sfmAddress;
    private final Selector selector;
    private final String serverName;
    private final Thread console;
    private final StringController stringController = new StringController();
    private final SocketChannel sfmSocketChannel;
    private final Map<String, Context> serversConnected = new HashMap<>();
    private final Map<String, Context> usersConnected = new HashMap<>();
    private Map<String, SocketAddressToken> routes = new HashMap<>();
    private boolean sfmIsConnected = false;
    private Context uniqueSFMContext;


    public ServerChatFusion(String serverName, int port, InetSocketAddress sfmAddress) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        this.sfmAddress = sfmAddress;
        this.serverName = serverName;
        this.console = new Thread(this::consoleRun);
        this.sfmSocketChannel = SocketChannel.open();
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
                "name=" + serverName +
                ", sfmAddress=" + sfmAddress +
                '}';
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
        if (command.startsWith("FUSION")) {
            if (!sfmIsConnected) {
                System.out.println("ServerFusionManager is unreachable, fusion is not possible");
            } else {
                var token = command.split(" ");
                var address = token[1];
                var port = token[2];
                //uniqueSFMContext.queueCommand(new FusionInit(address, Integer.parseInt(port)));
            }
        } else {
            switch (command) {
                case "INFO" -> processInstructionInfo();
                case "SHUTDOWN" -> processInstructionShutdown();
                case "SHUTDOWNNOW" -> processInstructionShutdownNow();
                default -> System.out.println("Unknown command");
            }
        }
    }

    private int nbInterlocutorActuallyConnected(Context.Interlocutor interlocutor) {
        return selector.keys().stream().mapToInt(key -> {
            if (key.isValid() && key.channel() != serverSocketChannel) {
                Context context = (Context) key.attachment(); // safeCase
                if(context.interlocutor == interlocutor){
                    return 1;
                }
                return 0;
            } else {
                return 0;
            }
        }).sum();
    }

    private void processInstructionInfo() {
        System.out.println("Unregistered connected : " + nbInterlocutorActuallyConnected(Context.Interlocutor.UNKNOWN));
        System.out.println("Clients connected      : " + nbInterlocutorActuallyConnected(Context.Interlocutor.CLIENT));
        System.out.println("Servers connected      : " + nbInterlocutorActuallyConnected(Context.Interlocutor.SERVER));
        if(nbInterlocutorActuallyConnected(Context.Interlocutor.SFM) == 1){
            System.out.println("SFM connected          : yes");
        }
        else{
            System.out.println("SFM connected          : no");
        }
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
        System.out.println(this);
        console.start();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        try {
            sfmSocketChannel.configureBlocking(false);
            var key = sfmSocketChannel.register(selector, SelectionKey.OP_CONNECT);
            uniqueSFMContext = new Context(key, this, Context.Interlocutor.SFM);
            key.attach(uniqueSFMContext);
            sfmSocketChannel.connect(sfmAddress);
            sfmIsConnected = true;
        } catch (IOException ioe) {
            System.out.println("ServerFusionManager is unreachable");
            sfmIsConnected = false;
        }
        //registerToServerFusionManager();

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
            if (key.isValid() && key.isConnectable()) {
                // SFM or Server new connexion
                ((Context) key.attachment()).doConnect();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with server/SFM due to IOException", e);
            silentlyClose(key);
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
        selectionKey.attach(new Context(selectionKey, this, Context.Interlocutor.UNKNOWN));
    }

    private void silentlyClose(SelectionKey key) {
        Channel sc = key.channel();
        try {
            sc.close();
        } catch (IOException e) {
            // ignore exception
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           server commands processing                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean processInInterlocutorUnknown(Context context) {
        // we attempt LOGIN_ANONYMOUS(0), SERVER_CONNEXION(15)

        if (context.currentCommand == 0) {
            switch (context.loginAnonymousReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var loginAnonymous = context.loginAnonymousReader.get();
                    context.loginAnonymousReader.reset();
                    if (usersConnected.containsKey(loginAnonymous.login())) {
                        //context.queueCommand(new LoginRefused().toBuffer());
                        System.out.println("Connexion denied : login already used");
                    } else {
                        context.interlocutor = Context.Interlocutor.CLIENT;
                        usersConnected.put(loginAnonymous.login(), context);
                        context.queueCommand(new LoginAccepted(serverName).toBuffer());
                        System.out.println("Connexion success : " + loginAnonymous.login());
                    }
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else if (context.currentCommand == 15) {
            switch (context.serverConnexionReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var serverConnexion = context.serverConnexionReader.get();
                    context.serverConnexionReader.reset();
                    // TODO Check if the current interlocutor is really a cluster's server
                    context.interlocutor = Context.Interlocutor.SERVER;
                    serversConnected.put(serverConnexion.name(), context);
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else {
            System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInInterlocutorUnknown");
        }
        return false;
    }

    private boolean processInSFM(Context context) {
        // we attempt FUSION_INEXISTANT_SERVER(10)
        // FUSION_ROUTE_TABLE_ASK(11)
        // FUSION_INVALID_NAME(13)
        // FUSION_TABLE_ROUTE_RESULT(14)

        if (context.currentCommand == 10) {
            System.out.println("The server you are trying to merge with does not exist");
            context.readingState = Context.ReadingState.WAITING_OPCODE;
            return false;

        } else if (context.currentCommand == 11) {
            // sending routes table
            context.queueCommand(new FusionRouteTableSend(routes.size(), routes).toBuffer());
            context.readingState = Context.ReadingState.WAITING_OPCODE;
            return false;

        } else if (context.currentCommand == 13) {
            System.out.println("Merge is not possible server names are not all distinct");
            context.readingState = Context.ReadingState.WAITING_OPCODE;
        } else if (context.currentCommand == 14) {
            switch (context.fusionTableRouteResultReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var fusionTableRouteResult = context.fusionTableRouteResultReader.get();
                    context.fusionTableRouteResultReader.reset();
                    routes = fusionTableRouteResult.routes();
                    for (var routesName : routes.keySet()) {
                        if (serverName.compareTo(routesName) < 0 && !serversConnected.containsKey(routesName)) {
                            var address = routes.get(routesName).getStringAddress();
                            var port = routes.get(routesName).port();
                            registerToAnotherServer(routesName, new InetSocketAddress(address, port));
                        }
                    }
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else {
            System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInSFM");
        }
        return false;
    }

    private boolean processInServer(Context context) {
        // we attempt MESSAGE_PUBLIC_TRANSMIT(5)
        // MESSAGE_PRIVATE(6)
        // FILE_PRIVATE(7)

        if (context.currentCommand == 5) {
            // TODO MESSAGE_PUBLIC_TRANSMIT
            /*switch (context.messagePublicTransmitReader.process(context.bufferIn)) {*/
            switch (context.messagePublicSendReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {return true;}
                case DONE -> {
                    var messagePublicSend = context.messagePublicSendReader.get();
                    context.messagePublicSendReader.reset();
                    broadcastPublicMessage(messagePublicSend.toBuffer());
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else if (context.currentCommand == 6) {
            switch (context.messagePrivateReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var messagePrivate = context.messagePrivateReader.get();
                    context.messagePrivateReader.reset();
                    if (usersConnected.containsKey(messagePrivate.loginDst())) {
                        var userContext = usersConnected.get(messagePrivate.loginDst());
                        userContext.queueCommand(messagePrivate.toBuffer());
                    }
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else if (context.currentCommand == 7) {
            // TODO FILE_PRIVATE(7);
        } else {
            System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInServer");
        }
        return false;
    }

    private boolean processInClient(Context context) {
        // we attempt MESSAGE_PUBLIC_SEND(4)
        // MESSAGE_PRIVATE(6)
        // FILE_PRIVATE(7)

        if (context.currentCommand == 4) {
            switch (context.messagePublicSendReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var messagePublicSend = context.messagePublicSendReader.get();
                    context.messagePublicSendReader.reset();
                    broadcastPublicMessage(messagePublicSend.toBuffer());
                    transmitsPublicMessageSendingByClient(messagePublicSend.toBuffer());
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else if (context.currentCommand == 6) {
            switch (context.messagePrivateReader.process(context.bufferIn)) {
                case ERROR -> context.silentlyClose();
                case REFILL -> {
                    return true;
                }
                case DONE -> {
                    var messagePrivate = context.messagePrivateReader.get();
                    context.messagePrivateReader.reset();
                    if (messagePrivate.serverDst().equals(serverName)) {
                        if (usersConnected.containsKey(messagePrivate.loginDst())) {
                            var destContext = usersConnected.get(messagePrivate.loginDst());
                            destContext.queueCommand(messagePrivate.toBuffer());
                        }
                    } else {
                        if (serversConnected.containsKey(messagePrivate.serverDst())) {
                            var nextServContext = serversConnected.get(messagePrivate.serverDst());
                            nextServContext.queueCommand(messagePrivate.toBuffer());
                        }
                    }
                    context.readingState = Context.ReadingState.WAITING_OPCODE;
                    return false;
                }
            }
        } else if (context.currentCommand == 7) {
            // TODO FILE_PRIVATE(7)
        } else {
            //System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInClient");
            //processInstructionInfo();
        }
        return false;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           server sending commands                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void registerToServerFusionManager() {
        if(sfmIsConnected){
            uniqueSFMContext.queueCommand(new FusionRegisterServer(serverName).toBuffer());
        }
    }

    private void registerToAnotherServer(String serverDestName, InetSocketAddress address) {
        SocketChannel sc;
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(false);
            var key = sc.register(selector, SelectionKey.OP_CONNECT);
            var serverContext = new Context(key, this, Context.Interlocutor.SERVER);
            key.attach(serverContext);
            sc.connect(address);
            serverContext.queueCommand(new /*ServerConnexion*/FusionRegisterServer(serverName).toBuffer());
            serversConnected.put(serverDestName, serverContext);

        } catch (IOException e) {
            logger.severe("OUT CONNEXION WITH ANOTHER SERVER FINISHED BY IOEXCEPTION");
        }
    }

    /**
     * Add a text to all connected clients queue
     *
     * @param cmd - text to add to all connected clients queue
     */
    private void broadcastPublicMessage(ByteBuffer cmd) {
        Objects.requireNonNull(cmd);
        for (SelectionKey key : selector.keys()) {
            if (key.channel() == serverSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment(); // Safe Cast
            if (context.interlocutor == Context.Interlocutor.CLIENT) {
                context.queueCommand(cmd.duplicate()); // duplicate allows us not to copy the data
            }
        }
    }

    private void transmitsPublicMessageSendingByClient(ByteBuffer cmd) {
        Objects.requireNonNull(cmd);
        for (SelectionKey key : selector.keys()) {
            if (key.channel() == serverSocketChannel) {
                continue;
            }
            Context context = (Context) key.attachment(); // Safe Cast
            if (context.interlocutor == Context.Interlocutor.SERVER) {
                context.queueCommand(cmd.duplicate()); // duplicate allows us not to copy the data
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {

        private final SelectionKey key;
        private final SocketChannel sc;
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        // readers
        private final BytesReader opcodeReader = new BytesReader(1);
        private final LoginAnonymousReader loginAnonymousReader = new LoginAnonymousReader();
        private final MessagePublicSendReader messagePublicSendReader = new MessagePublicSendReader();
        // TODO message transmits reader
        private final MessagePrivateReader messagePrivateReader = new MessagePrivateReader();
        // TODO file private reader
        // TODO fusion route table ask reader
        private final FusionTableRouteResultReader fusionTableRouteResultReader = new FusionTableRouteResultReader();
        // TODO need to change to a real serverConnexionReader !!
        private final FusionRegisterServerReader serverConnexionReader = new FusionRegisterServerReader();
        // give access to ServerChatFusion.this
        private boolean closed = false;
        private Writer writer = null;
        private ReadingState readingState = ReadingState.WAITING_OPCODE;
        private Interlocutor interlocutor;
        private byte currentCommand;


        private Context(SelectionKey key, ServerChatFusion server, Interlocutor interlocutor) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
            this.interlocutor = interlocutor;
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                if(assureCurrentCommandSet()) return;
                switch (interlocutor) {
                    case UNKNOWN -> {
                        if (server.processInInterlocutorUnknown(this)) return;
                    }
                    case CLIENT -> {
                        if (server.processInClient(this)) return;
                    }
                    case SERVER -> {
                        if (server.processInServer(this)) return;
                    }
                    case SFM -> {
                        if (server.processInSFM(this)) return;
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
            System.out.println("add queue cmd...");
        }

        /**
         * Assure currentCommand is set
         */
        private boolean assureCurrentCommandSet() {
            if (readingState == ReadingState.WAITING_OPCODE) {
                switch (opcodeReader.process(bufferIn))
                {
                    case ERROR -> silentlyClose();
                    case REFILL -> {return true;}
                    case DONE -> {
                        currentCommand = opcodeReader.get()[0];
                        opcodeReader.reset();
                        readingState = ReadingState.PROCESS_IN;
                    }
                }
            }
            return false;
        }

        /**
         * Try to fill bufferOut from the command queue
         */
        private void processOut() {
            while (!queueCommand.isEmpty() && bufferOut.hasRemaining()) {
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

        public void doConnect() throws IOException {
            if (!sc.finishConnect())
                return; // the selector gave a bad hint
            key.interestOps(SelectionKey.OP_READ); // need OP_WRITE to send FUSION_REGISTER_SERVER(8) or SERVER_CONNEXION(15)
        }

        enum ReadingState {
            WAITING_OPCODE,
            PROCESS_IN,
        }

        private enum Interlocutor {
            CLIENT, SERVER, SFM, UNKNOWN
        }
    }
}