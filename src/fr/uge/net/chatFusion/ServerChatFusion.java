package fr.uge.net.chatFusion;

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

import static fr.uge.net.chatFusion.reader.Reader.ProcessStatus;

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
    //private final Map<String, Context> serversConnected = new HashMap<>();
    private final Map<EntryRouteTable, Context> serversConnected = new HashMap<>();

    private final Map<String, Context> usersConnected = new HashMap<>();
    private Map<String, SocketAddressToken> routes = new HashMap<>();

    private boolean sfmIsConnected = false;
    private Context uniqueSFMContext;
    private boolean firstLoop = true;
    private final InetSocketAddress serverAddress;
    private final int port;

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
                if(inetAddress == null){
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
        if (nbInterlocutorActuallyConnected(Context.Interlocutor.SFM) == 1) {
            System.out.println("SFM connected          : yes");
        } else {
            System.out.println("SFM connected          : no");
        }
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


        while (!Thread.interrupted()) {
            //Helpers.printKeys(selector); // for debug
            //System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                processInstructions();
                if(firstLoop){
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

        try {
            if (key.isValid() && key.isConnectable()) {
                // SFM or Server new connexion
                ((Context) key.attachment()).doConnect();
            }
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

    /**
     * Return true if bufferIn attach to Context is empty
     */
    private ProcessStatus processInInterlocutorUnknown(Context context) {
        // we attempt LOGIN_ANONYMOUS(0), SERVER_CONNEXION(15)
        switch (context.currentCommand) {
            case 0 -> {
                switch (context.loginAnonymousReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var loginAnonymous = context.loginAnonymousReader.get();
                        context.loginAnonymousReader.reset();
                        if (usersConnected.containsKey(loginAnonymous.login())) {
                            context.queueCommand(new LoginRefused().toBuffer());
                            System.out.println("Connexion denied : login already used");
                        } else {
                            context.interlocutor = Context.Interlocutor.CLIENT;
                            usersConnected.put(loginAnonymous.login(), context);
                            context.queueCommand(new LoginAccepted(serverName).toBuffer());
                            System.out.println("Connexion success : " + loginAnonymous.login());
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            case 15 -> {
                switch (context.serverConnexionReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var serverConnexion = context.serverConnexionReader.get();
                        context.serverConnexionReader.reset();
                        // TODO Check if the current interlocutor is really a cluster's server
                        context.interlocutor = Context.Interlocutor.SERVER;
                        serversConnected.put(new EntryRouteTable(serverConnexion.name(), serverConnexion.socketAddressToken()) , context);
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            default -> {
                logger.info("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInInterlocutorUnknown");
                context.silentlyClose();
            }
        }
        return ProcessStatus.DONE;
    }

    private ProcessStatus processInSFM(Context context) {
        // we attempt FUSION_INEXISTANT_SERVER(10)
        // FUSION_ROUTE_TABLE_ASK(11)
        // FUSION_INVALID_NAME(13)
        // FUSION_TABLE_ROUTE_RESULT(14)

        switch (context.currentCommand) {
            case 10 -> {
                System.out.println("The server you are trying to merge with does not exist");
                context.readingState = Context.ReadingState.WAITING_OPCODE;
            }
            case 11 -> {
                context.queueCommand(new FusionRouteTableSend(routes.size(), routes).toBuffer());
                context.readingState = Context.ReadingState.WAITING_OPCODE;
            }
            case 13 -> {
                System.out.println("Merge is not possible server names are not all distinct");
                context.readingState = Context.ReadingState.WAITING_OPCODE;
            }
            case 14 -> {
                switch (context.fusionTableRouteResultReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var fusionTableRouteResult = context.fusionTableRouteResultReader.get();
                        context.fusionTableRouteResultReader.reset();
                        routes = fusionTableRouteResult.routes();
                        for (var routeName : routes.keySet()) {
                            if (serverName.compareTo(routeName) < 0 && !serversConnected.containsKey(new EntryRouteTable(routeName, routes.get(routeName)))) {
                                var address = routes.get(routeName).address().getHostAddress();
                                var port = routes.get(routeName).port();
                                registerToAnotherServer(routeName, new InetSocketAddress(address, port));
                            }
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;

                    }
                }
            }
            default -> {
                System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInSFM");
                context.silentlyClose();
                return ProcessStatus.ERROR;
            }
        }
        return ProcessStatus.DONE;
    }

    private ProcessStatus processInServer(Context context) {
        // we attempt MESSAGE_PUBLIC_TRANSMIT(5)
        // MESSAGE_PRIVATE(6)
        // FILE_PRIVATE(7)

        switch (context.currentCommand) {
            case 5 -> {
                switch (context.messagePublicTransmitReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var messagePublicSend = context.messagePublicSendReader.get();
                        context.messagePublicSendReader.reset();
                        broadcastPublicMessage(messagePublicSend.toBuffer());
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            case 6 -> {
                switch (context.messagePrivateReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var messagePrivate = context.messagePrivateReader.get();
                        context.messagePrivateReader.reset();

                        if (messagePrivate.serverDst().equals(serverName)) {
                            if (usersConnected.containsKey(messagePrivate.loginDst())) {
                                var userContext = usersConnected.get(messagePrivate.loginDst());
                                userContext.queueCommand(messagePrivate.toBuffer());
                            } else {
                                System.out.println("message private drop, client unknown");
                            }
                        } else {
                            var entry = serversConnected.keySet().stream().filter(name -> name.name().equals(messagePrivate.serverDst())).findAny();
                            if(entry.isEmpty()){
                                System.out.println("message private drop, server unknown");
                            }
                            else{
                                var serverContext = serversConnected.get(entry.get());
                                serverContext.queueCommand(messagePrivate.toBuffer());
                            }
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }

            case 7 -> {
                switch (context.filePrivateReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var filePrivate = context.filePrivateReader.get();
                        context.filePrivateReader.reset();

                        if (filePrivate.serverDst().equals(serverName)) {
                            if (usersConnected.containsKey(filePrivate.loginDst())) {
                                var userContext = usersConnected.get(filePrivate.loginDst());
                                userContext.queueCommand(filePrivate.toBuffer());
                            } else {
                                System.out.println("file private drop, client unknown");
                            }
                        } else {

                            var entry = serversConnected.keySet().stream().filter(name -> name.name().equals(filePrivate.serverDst())).findAny();
                            if(entry.isEmpty()){
                                System.out.println("file private drop, server unknown");
                            }
                            else{
                                var serverContext = serversConnected.get(entry.get());
                                serverContext.queueCommand(filePrivate.toBuffer());
                            }
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            default -> {
                System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInServer");
                context.silentlyClose();
                return ProcessStatus.ERROR;
            }
        }
        return ProcessStatus.DONE;
    }

    private ProcessStatus processInClient(Context context) {
        // we attempt MESSAGE_PUBLIC_SEND(4)
        // MESSAGE_PRIVATE(6)
        // FILE_PRIVATE(7)

        switch (context.currentCommand) {
            case 4 -> {
                switch (context.messagePublicSendReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var messagePublicSend = context.messagePublicSendReader.get();
                        context.messagePublicSendReader.reset();

                        var messagePublicTransmit = new MessagePublicTransmit(messagePublicSend.serverSrc(),
                                messagePublicSend.loginSrc(),
                                messagePublicSend.msg());
                        broadcastPublicMessage(messagePublicTransmit.toBuffer());
                        transmitsPublicMessageSendingByClient(messagePublicTransmit.toBuffer());
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            case 6 -> {
                switch (context.messagePrivateReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var messagePrivate = context.messagePrivateReader.get();
                        context.messagePrivateReader.reset();
                        if (messagePrivate.serverDst().equals(serverName)) {
                            if (usersConnected.containsKey(messagePrivate.loginDst())) {
                                var destContext = usersConnected.get(messagePrivate.loginDst());
                                destContext.queueCommand(messagePrivate.toBuffer());
                            } else {
                                System.out.println("message private drop, client unknown");
                            }
                        } else {
                            var entry = serversConnected.keySet().stream().filter(name -> name.name().equals(messagePrivate.serverDst())).findAny();
                            if(entry.isEmpty()){
                                System.out.println("message private drop, server unknown");
                            }
                            else{
                                var serverContext = serversConnected.get(entry.get());
                                serverContext.queueCommand(messagePrivate.toBuffer());
                            }
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            case 7 -> {
                switch (context.filePrivateReader.process(context.bufferIn)) {
                    case ERROR -> {
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        var filePrivate = context.filePrivateReader.get();
                        context.filePrivateReader.reset();
                        if (filePrivate.serverDst().equals(serverName)) {
                            if (usersConnected.containsKey(filePrivate.loginDst())) {
                                var destContext = usersConnected.get(filePrivate.loginDst());
                                destContext.queueCommand(filePrivate.toBuffer());
                            }
                        } else {
                            var entry = serversConnected.keySet().stream().filter(name -> name.name().equals(filePrivate.serverDst())).findAny();
                            if(entry.isEmpty()){
                                System.out.println("message private drop, server unknown");
                            }
                            else{
                                var serverContext = serversConnected.get(entry.get());
                                serverContext.queueCommand(filePrivate.toBuffer());
                            }
                        }
                        context.readingState = Context.ReadingState.WAITING_OPCODE;
                    }
                }
            }
            default -> {
                System.out.println("BAD RECEIVING COMMAND : " + context.currentCommand + " in processInClient");
                context.silentlyClose();
                return ProcessStatus.ERROR;
            }
        }
        return ProcessStatus.DONE;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                           server sending commands                                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void registerToServerFusionManager() {
        if (sfmIsConnected) {
            uniqueSFMContext.queueCommand(new FusionRegisterServer(serverName, new SocketAddressToken(serverAddress.getAddress(), port)).toBuffer());
        }
    }

    /**
     * Init a new connexion with serverDestName reachable at address
     * */
    private void registerToAnotherServer(String serverDestName, InetSocketAddress addressDest) {
        SocketChannel sc;
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(false);
            var key = sc.register(selector, SelectionKey.OP_CONNECT);
            var serverContext = new Context(key, this, Context.Interlocutor.SERVER);
            key.attach(serverContext);
            sc.connect(addressDest);
            serverContext.queueCommand(new ServerConnexion(serverName, new SocketAddressToken(serverAddress.getAddress(), port)).toBuffer());
            serversConnected.put(new EntryRouteTable(serverDestName, new SocketAddressToken(addressDest.getAddress(), addressDest.getPort())), serverContext);

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

    private void transmitsPublicMessageSendingByClient(ByteBuffer cmd) {
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
        private final ServerChatFusion server; // we could also have Context as an instance class, which would naturally
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        // readers
        private final BytesReader opcodeReader = new BytesReader(1);
        private final LoginAnonymousReader loginAnonymousReader = new LoginAnonymousReader();
        private final MessagePublicSendReader messagePublicSendReader = new MessagePublicSendReader();
        private final MessagePublicTransmitReader messagePublicTransmitReader = new MessagePublicTransmitReader();
        private final MessagePrivateReader messagePrivateReader = new MessagePrivateReader();
        private final FilePrivateReader filePrivateReader = new FilePrivateReader();
        private final FusionTableRouteResultReader fusionTableRouteResultReader = new FusionTableRouteResultReader();
        private final ServerConnexionReader serverConnexionReader = new ServerConnexionReader();
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
                switch (assureCurrentCommandSet()) {
                    case ERROR -> {
                        silentlyClose();
                        return;
                    }
                    case REFILL -> {
                        return;
                    }
                    case DONE -> {
                        switch (interlocutor) {
                            case UNKNOWN -> {
                                switch (server.processInInterlocutorUnknown(this)) {
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

                            case CLIENT -> {
                                switch (server.processInClient(this)) {
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
                            case SERVER -> {
                                switch (server.processInServer(this)) {
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
                            case SFM -> {
                                switch (server.processInSFM(this)) {
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
                        return ProcessStatus.ERROR;
                    }
                    case REFILL -> {
                        return ProcessStatus.REFILL;
                    }
                    case DONE -> {
                        currentCommand = opcodeReader.get()[0];
                        opcodeReader.reset();
                        readingState = ReadingState.PROCESS_IN;
                    }
                }
            }
            return ProcessStatus.DONE;
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
                    server.serversConnected.remove(server.retrieveServerEntryFromContext(this));
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
            sc.write(bufferOut);
            bufferOut.compact();
            processOut();
            updateInterestOps();
        }

        public void doConnect() throws IOException {
            if (!sc.finishConnect())
                return; // the selector gave a bad hint
            key.interestOps(SelectionKey.OP_READ); // needed OP_WRITE to send FUSION_REGISTER_SERVER(8) or SERVER_CONNEXION(15) ?
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