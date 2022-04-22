package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.util.FrameVisitor;
import fr.uge.net.chatFusion.command.*;
import fr.uge.net.chatFusion.reader.*;
import fr.uge.net.chatFusion.util.EntryRouteTable;
import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
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
    private final Map<EntryRouteTable, Context> serversConnected = new HashMap<>();

    private final Map<String, Context> usersConnected = new HashMap<>();
    private final InetSocketAddress serverAddress;
    private final int port;
    private Map<String, SocketAddressToken> routes = new HashMap<>();
    private boolean sfmIsConnected = false;
    private Context uniqueSFMContext;
    private boolean firstLoop = true;

    public ServerChatFusion(String serverName, int port, InetSocketAddress sfmAddress) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverAddress = new InetSocketAddress("localhost", port);
        this.port = port;
        serverSocketChannel.bind(serverAddress);
        selector = Selector.open();
        this.sfmAddress = sfmAddress;
        this.serverName = serverName;
        this.console = new Thread(this::consoleRun);
        this.sfmSocketChannel = SocketChannel.open();
        routes.put(serverName, new SocketAddressToken(serverAddress.getAddress(), port));
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

    private Optional<EntryRouteTable> retrieveEntryRouteFromServerName(String serverName) {
        return serversConnected.keySet().stream().filter(name -> name.name().equals(serverName)).findAny();
    }

    private String retrieveClientNameFromContext(Context context) {
        if (context.interlocutor != Context.Interlocutor.CLIENT) {
            throw new AssertionError("This is not a client");
        }
        for (var clientLogin : usersConnected.keySet()) {
            if (usersConnected.get(clientLogin) == context) {
                return clientLogin;
            }
        }
        throw new AssertionError("Context is not in usersConnected map");
    }

    private EntryRouteTable retrieveServerEntryFromContext(Context context) {
        if (context.interlocutor != Context.Interlocutor.SERVER) {
            throw new AssertionError("This is not a server");
        }
        for (var entry : serversConnected.keySet()) {
            if (serversConnected.get(entry) == context) {
                return entry;
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
        if (command.toUpperCase().startsWith("FUSION")) {
            if (!sfmIsConnected) {
                System.out.println("ServerFusionManager is unreachable, fusion is not possible");
                return;
            }
            var tokens = command.split(" ");
            if (tokens.length != 3) {
                System.out.println("Invalid syntax : FUSION address port");
                return;
            }
            var addressString = tokens[1];
            int port;
            InetAddress inetAddress;
            try {
                port = Integer.parseInt(tokens[2]);
                inetAddress = new InetSocketAddress(addressString, port).getAddress();
                if (inetAddress == null) {
                    System.out.println("Given address is unresolved");
                    return;
                }
                System.out.println("Fusion init with server " + inetAddress + ":" + port + "...");
                //System.out.println("BUFFER ENVOYEE = " + new FusionInit(new SocketAddressToken(inetAddress, port)).toBuffer());
                uniqueSFMContext.queueCommand(new FusionInit(new SocketAddressToken(inetAddress, port)).toBuffer());
            } catch (NumberFormatException e) {
                System.out.println("Wrong port given");
            } catch (IllegalArgumentException e) {
                System.out.println("Port is outside the range of valid port values");
            } catch (SecurityException e) {
                System.out.println("Security manager is present and permission to resolve the host name is denied");
            }
        } else {
            switch (command.toUpperCase()) {
                case "INFO" -> processInstructionInfo();
                case "INFOCOMPLETE" -> processInstructionInfoComplete();
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
                if (context.interlocutor == interlocutor) {
                    return 1;
                }
            }
            return 0;
        }).sum();
    }

    private void processInstructionInfo() {
        System.out.println("Unregistered connected : " + nbInterlocutorActuallyConnected(Context.Interlocutor.UNKNOWN));
        System.out.println("Clients connected      : " + nbInterlocutorActuallyConnected(Context.Interlocutor.CLIENT));
        System.out.println("Servers connected      : " + nbInterlocutorActuallyConnected(Context.Interlocutor.SERVER));
        System.out.println("SFM connected          : " + (nbInterlocutorActuallyConnected(Context.Interlocutor.SFM) == 1 ? "yes" : "no"));
    }

    private void processInstructionInfoComplete() {

        System.out.println("============================================");
        System.out.println("Selector information (reality) :");
        processInstructionInfo();

        System.out.println("\nMaps information (suppose) :");
        System.out.print("users connected map   -> ");
        final StringJoiner sj = new StringJoiner(", ", "{", "}");
        usersConnected.forEach((key, value) -> sj.add(key));
        System.out.println(sj);

        System.out.print("servers connected map -> ");
        final StringJoiner sj2 = new StringJoiner(", ", "{", "}");
        serversConnected.forEach((key, value) -> sj2.add(key.name() + key.socketAddressToken().address()));
        System.out.println(sj2);
        System.out.println("SFM connected          : " + (sfmIsConnected ? "yes" : "no"));
        System.out.println("============================================");
    }

    private void processInstructionShutdown() {
        System.out.println("shutdown...");
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            logger.severe("IOESHUTDOWN" + e.getCause());
        }
    }

    private void processInstructionShutdownNow() throws IOException {
        System.out.println("shutdown now...");
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

        connexionToSFM();

        while (!Thread.interrupted()) {
            //Helpers.printKeys(selector); // for debug
            //System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processInstructions();
                if (firstLoop && sfmIsConnected) {
                    registerToServerFusionManager();
                    firstLoop = false;
                    sfmIsConnected = true;
                }
            } catch (UncheckedIOException tunneled) {
                throw tunneled.getCause();
            }
            //System.out.println("Select finished");
        }
    }

    private void connexionToSFM() {
        try {
            sfmSocketChannel.configureBlocking(false);
            var key = sfmSocketChannel.register(selector, SelectionKey.OP_CONNECT);
            uniqueSFMContext = new Context(key, this, Context.Interlocutor.SFM);
            uniqueSFMContext.frameVisitor = new ToSFMVisitor(uniqueSFMContext, this);
            key.attach(uniqueSFMContext);
            sfmSocketChannel.connect(sfmAddress);
            sfmIsConnected = true;
        } catch (IOException ioe) {
            System.out.println("ServerFusionManager is unreachable");
            sfmIsConnected = false;
        }
    }

    private void treatKey(SelectionKey key) {
        //Helpers.printSelectedKey(key); // for debug
        try {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            throw new UncheckedIOException(ioe);
        }

        if (key.isValid() && key.isConnectable()) {
            // SFM or Server new connexion
            try {
                ((Context) key.attachment()).doConnect();
            } catch (IOException e) {
                Context context = (Context) key.attachment();
                if (context.interlocutor == Context.Interlocutor.SFM) {
                    System.out.println("SFM is unreachable");
                    sfmIsConnected = false;
                } else {
                    logger.log(Level.INFO, "Connection closed with interlocutor due to IOException", e);
                }
            }
        }


        try {
            if (key.isValid() && key.isWritable()) {
                ((Context) key.attachment()).doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                ((Context) key.attachment()).doRead();
            }
        } catch (IOException e) {
            logger.log(Level.INFO, "Connection closed with interlocutor due to IOException", e);
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
        System.out.println("NEW CONNEXION FROM " + sc.getRemoteAddress());
        processInstructionInfoComplete();
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
    //                                           server sending commands                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void registerToServerFusionManager() {
        if (sfmIsConnected) {
            uniqueSFMContext.queueCommand(new FusionRegisterServer(serverName, new SocketAddressToken(serverAddress.getAddress(), port)).toBuffer());
        }
    }

    private void connectToAnotherServer(String serverDestName, InetSocketAddress addressDest) {
        try {
            SocketChannel sc = SocketChannel.open();
            sc.configureBlocking(false);
            var key = sc.register(selector, SelectionKey.OP_CONNECT);
            var serverContext = new Context(key, this, Context.Interlocutor.SERVER);
            serverContext.frameVisitor = new ToServerVisitor(serverContext, this);
            key.attach(serverContext);
            sc.connect(addressDest);
            System.out.println("Connected with " + addressDest.getAddress() + ":" + addressDest.getPort());
            var serverAddress = new SocketAddressToken(addressDest.getAddress(), addressDest.getPort());
            serversConnected.put(new EntryRouteTable(serverDestName, serverAddress), serverContext);
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
            if (key.channel() != serverSocketChannel) {
                Context context = (Context) key.attachment(); // Safe Cast
                if (context.interlocutor == Context.Interlocutor.CLIENT) {
                    context.queueCommand(cmd.duplicate()); // duplicate allows us not to copy the data
                }
            }
        }
    }

    private void broadcastToCluster(ByteBuffer cmd) {
        Objects.requireNonNull(cmd);
        for (SelectionKey key : selector.keys()) {
            if (key.channel() != serverSocketChannel) {
                Context context = (Context) key.attachment(); // Safe Cast
                if (context.interlocutor == Context.Interlocutor.SERVER) {
                    context.queueCommand(cmd.duplicate()); // duplicate allows us not to copy the data
                }
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {

        private final SelectionKey key;
        private final SocketChannel sc;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        // readers
        private final FrameReader frameReader = new FrameReader();
        private FrameVisitor frameVisitor;
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        // give access to ServerChatFusion.this
        private boolean closed = false;
        private Writer writer = null;
        private Interlocutor interlocutor;

        private Context(SelectionKey key, ServerChatFusion server, Interlocutor interlocutor) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.server = server;
            this.interlocutor = interlocutor;
            frameVisitor = new InterlocutorUnknownVisitor(this, server);
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process and
         * after the call
         */
        private void processIn() {
            for (; ; ) {
                switch (frameReader.process(bufferIn)) {
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                    case REFILL -> {
                        return;
                    }
                    case DONE -> {
                        Frame frame = frameReader.get();
                        frameReader.reset();
                        treatFrame(frame);
                    }
                }
            }
        }

        private void treatFrame(Frame frame) {
            frame.accept(frameVisitor);
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
            switch (interlocutor) {
                case CLIENT -> {
                    server.usersConnected.remove(server.retrieveClientNameFromContext(this));
                    System.out.println("remove client from map");
                }
                case UNKNOWN -> {
                }
                case SFM -> {
                    server.sfmIsConnected = false;
                    System.out.println("Disconnected with ServerFusionManager");
                }
                case SERVER -> {
                    var serverEntry = server.retrieveServerEntryFromContext(this);
                    server.serversConnected.remove(serverEntry);
                    server.routes.remove(serverEntry.name());
                    System.out.println("remove server from map");
                }
            }

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
            System.out.println("WROTE " + sc.write(bufferOut) + "bytes");
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect())
                return; // the selector gave a bad hint
            System.out.println("NEW SORTANTE CONNEXION");
            key.interestOps(SelectionKey.OP_READ); // needed OP_WRITE to send FUSION_REGISTER_SERVER(8) or SERVER_CONNEXION(15) ?

            if (interlocutor == Interlocutor.SERVER) {
                System.out.println("Sending message 15 with info :  " + server.serverName + server.serverAddress.getAddress() + ":" + server.port);
                queueCommand(new ServerConnexion(server.serverName, new SocketAddressToken(server.serverAddress.getAddress(), server.port)).toBuffer());
            }
        }

        private enum Interlocutor {
            CLIENT, SERVER, SFM, UNKNOWN
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                          Visitors: contains the processing of messages by interlocutors                        //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class InterlocutorUnknownVisitor implements FrameVisitor {
        private final Context context;
        private final ServerChatFusion server;

        public InterlocutorUnknownVisitor(Context context, ServerChatFusion server) {
            this.context = context;
            this.server = server;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInit fusionInit) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            if (server.usersConnected.containsKey(loginAnonymous.login())) {
                context.queueCommand(new LoginRefused().toBuffer());
                System.out.println("Connexion denied : login already used");
                return;
            }
            context.interlocutor = Context.Interlocutor.CLIENT;
            context.frameVisitor = new ToClientVisitor(context, server);
            server.usersConnected.put(loginAnonymous.login(), context);
            context.queueCommand(new LoginAccepted(server.serverName).toBuffer());
            System.out.println("Connexion success : " + loginAnonymous.login());
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            context.silentlyClose();
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            System.out.println("new connexion from " + serverConnexion.name() + " at " + serverConnexion.socketAddressToken());
            // TODO Check if the current interlocutor is really a cluster's server
            context.interlocutor = Context.Interlocutor.SERVER;
            context.frameVisitor = new ToServerVisitor(context, server);
            server.serversConnected.put(new EntryRouteTable(serverConnexion.name(), serverConnexion.socketAddressToken()), context);
            System.out.println(serverConnexion.name() + serverConnexion.socketAddressToken().address() + ":" + serverConnexion.socketAddressToken().port() + " is now registered");
        }
    }

    static private class ToClientVisitor implements FrameVisitor {
        private final Context context;
        private final ServerChatFusion server;

        public ToClientVisitor(Context context, ServerChatFusion server) {
            this.context = context;
            this.server = server;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            if (filePrivate.serverDst().equals(server.serverName) && server.usersConnected.containsKey(filePrivate.loginDst())) {
                var destContext = server.usersConnected.get(filePrivate.loginDst());
                destContext.queueCommand(filePrivate.toBuffer());
                return;
            }
            var entry = server.retrieveEntryRouteFromServerName(filePrivate.serverDst());
            if (entry.isPresent()) {
                var serverContext = server.serversConnected.get(entry.get());
                serverContext.queueCommand(filePrivate.toBuffer());
            }
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInit fusionInit) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            if (messagePrivate.serverDst().equals(server.serverName) && server.usersConnected.containsKey(messagePrivate.loginDst())) {
                var destContext = server.usersConnected.get(messagePrivate.loginDst());
                destContext.queueCommand(messagePrivate.toBuffer());
                return;
            }
            var entry = server.retrieveEntryRouteFromServerName(messagePrivate.serverDst());
            if (entry.isPresent()) {
                var serverContext = server.serversConnected.get(entry.get());
                serverContext.queueCommand(messagePrivate.toBuffer());
            }
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            var messagePublicTransmit = new MessagePublicTransmit(messagePublicSend.serverSrc(),
                    messagePublicSend.loginSrc(),
                    messagePublicSend.msg());

            server.broadcastPublicMessage(messagePublicTransmit.toBuffer().duplicate());
            server.broadcastToCluster(messagePublicTransmit.toBuffer().duplicate());
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            context.silentlyClose();
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            context.silentlyClose();
        }
    }

    static private class ToSFMVisitor implements FrameVisitor {

        private final Context context;
        private final ServerChatFusion server;

        public ToSFMVisitor(Context context, ServerChatFusion server) {
            this.context = context;
            this.server = server;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            System.out.println("The server you are trying to merge with does not exist");
        }

        @Override
        public void visit(FusionInit fusionInit) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            System.out.println("Merge is not possible server names are not all distinct");
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            System.out.println("Sending of routes table... : " + new FusionRouteTableSend(server.routes.size(), server.routes).toBuffer());
            context.queueCommand(new FusionRouteTableSend(server.routes.size(), server.routes).toBuffer());
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            System.out.println("New Routes Table receive :");
            fusionTableRouteResult.routes().forEach((key, value) -> System.out.println(key + value.address() + " : " + value.port()));
            server.routes = fusionTableRouteResult.routes();
            for (var routeName : server.routes.keySet()) {
                var entry = new EntryRouteTable(routeName, server.routes.get(routeName));
                if (server.serverName.compareTo(routeName) < 0 && !server.serversConnected.containsKey(entry)) {
                    var address = server.routes.get(routeName).address().getHostAddress();
                    var port = server.routes.get(routeName).port();
                    server.connectToAnotherServer(routeName, new InetSocketAddress(address, port));
                    System.out.println("Sending new connexion ...");
                }
            }
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            context.silentlyClose();
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            context.silentlyClose();
        }
    }

    static private class ToServerVisitor implements FrameVisitor {
        private final Context context;
        private final ServerChatFusion server;

        public ToServerVisitor(Context context, ServerChatFusion server) {
            this.context = context;
            this.server = server;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            if (filePrivate.serverDst().equals(server.serverName) && server.usersConnected.containsKey(filePrivate.loginDst())) {
                var userContext = server.usersConnected.get(filePrivate.loginDst());
                userContext.queueCommand(filePrivate.toBuffer());
                return;
            }
            var entry = server.retrieveEntryRouteFromServerName(filePrivate.serverDst());
            if (entry.isPresent()) {
                var serverContext = server.serversConnected.get(entry.get());
                serverContext.queueCommand(filePrivate.toBuffer());
            }
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInit fusionInit) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            context.silentlyClose();
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            if (messagePrivate.serverDst().equals(server.serverName) && server.usersConnected.containsKey(messagePrivate.loginDst())) {
                var userContext = server.usersConnected.get(messagePrivate.loginDst());
                userContext.queueCommand(messagePrivate.toBuffer());
                return;
            }
            var entry = server.retrieveEntryRouteFromServerName(messagePrivate.serverDst());
            if (entry.isPresent()) {
                var serverContext = server.serversConnected.get(entry.get());
                serverContext.queueCommand(messagePrivate.toBuffer());
            }
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            context.silentlyClose();
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            server.broadcastPublicMessage(messagePublicTransmit.toBuffer().duplicate());
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            context.silentlyClose();
        }
    }
}