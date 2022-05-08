package fr.uge.net.chatFusion;

import fr.uge.net.chatFusion.command.*;
import fr.uge.net.chatFusion.reader.*;
import fr.uge.net.chatFusion.util.FrameVisitor;
import fr.uge.net.chatFusion.util.StringController;
import fr.uge.net.chatFusion.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;

/**
 * Implements Client according to FusionManager version fo ChatFusion.
 */
public class ClientChatFusion {

    static private final int BUFFER_SIZE = 10_000;
    static private final Logger logger = Logger.getLogger(ClientChatFusion.class.getName());
    private final SocketChannel sc;
    private final Selector selector;
    private final InetSocketAddress serverAddress;
    private String login; // no final to avoid restart instead of re-name in case of conflict with server
    private final Thread console;
    private final FileSender fileSender;
    private final StringController stringController = new StringController();
    private final String directory;
    private String serverName;
    private Context uniqueContext;
    private boolean firstSend = true;

    /**
     * <b>Constructor</b>
     * @param login the client login to use.
     * @param serverAddress The server addres to connect to.
     * @param directory the working directory of the client
     * @throws IOException
     */
    public ClientChatFusion(String login, InetSocketAddress serverAddress, String directory) throws IOException {
        this.fileSender = new FileSender(this);
        this.serverAddress = serverAddress;
        this.login = login;
        this.sc = SocketChannel.open();
        this.selector = Selector.open();
        this.console = new Thread(this::consoleRun);
        console.setDaemon(true);
        this.directory = directory;
    }

    private ByteBuffer buildFileChunk(byte[] data, String serverDst, String loginDst, String fileName, int nbChunk){
        return new FilePrivate(serverName, login, serverDst, loginDst, fileName, nbChunk, data.length, data).toBuffer();
    }

    private void eject(){
        System.out.println("OPCODE unknown or unexpected, drop command");
        uniqueContext.silentlyClose();
        Thread.currentThread().interrupt();
    }

    /**
     * Starts a client on the console.
     * @param args
     * @throws NumberFormatException
     * @throws IOException
     */
    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 4) {
            usage();
            return;
        }
        new ClientChatFusion(args[0], new InetSocketAddress(args[1], Integer.parseInt(args[2])), args[3]).launch();
    }

    private static void usage() {
        System.out.println("Usage : fr.uge.net.chatFusion.clientChatFusion.ClientChatFusion login hostname port directory");
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                            Console Thread + management of the user's instructions                              //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void consoleRun() {
        try {
            try (var scanner = new Scanner(System.in)) {
                while (scanner.hasNextLine()) {
                    var msg = scanner.nextLine();
                    sendInstruction(msg);
                }
            }
            logger.info("Console thread stopping");
        } catch (InterruptedException e) {
            logger.info("Console thread has been interrupted");
            //Thread.currentThread().interrupt();
        }
    }

    /**
     * Send instructions to the selector via stringController and wake it up
     *
     * @param instruction - input instruction
     * @throws InterruptedException - if thread has been interrupted
     */
    private void sendInstruction(String instruction) throws InterruptedException {
        stringController.add(instruction, selector);
        // Cause the exception if the main thread has requested the interrupt
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted by main thread");
        }
    }

    private ByteBuffer parseInstruction(String instruction) {
        if (!instruction.startsWith("/") && !instruction.startsWith("@")) {
            return new MessagePublicSend(serverName, login, instruction).toBuffer();
        }
        else {
                var tokens = instruction.substring(1).split(":", 2);
                if (tokens.length != 2){
                    System.out.println("saisie invalide!");
                }
                var loginDst = tokens[0];
                var serverDst = tokens[1].split(" ", 2)[0];

            if(instruction.startsWith("@")) {
                var msg = tokens[1].split(" ", 2)[1];
                return new MessagePrivate(serverName, login, serverDst, loginDst, msg).toBuffer();
            }
            if(instruction.startsWith("/")){
                System.out.println("detected file instruction");
                var filename = tokens[1].split(" ", 2)[1];
                var filePath = Path.of(directory, filename);
                try {
                    var fp = new FileSendInfo(filePath.toString(), loginDst, serverDst, filename);
                    fileSender.sendNewFile(fp);
                }catch(IOException ioe){
                    System.out.println("invalid file: " + filePath);
                }
                return ByteBuffer.allocate(0);
            }
        }
        return ByteBuffer.allocate(0);
    }

    /**
     * Processes the command from the messageController
     */
    private void processInstruction() {
        while (stringController.hasString()) {
            uniqueContext.queueCommand(parseInstruction(stringController.poll()));
        }
        // check if there is a file piece to send

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                selection loop                                                  //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Launches the client and its subprocesses
     * @throws IOException
     */
    public void launch() throws IOException {
        sc.configureBlocking(false);
        var key = sc.register(selector, SelectionKey.OP_CONNECT);
        uniqueContext = new Context(key, this);
        key.attach(uniqueContext);
        sc.connect(serverAddress);
        //uniqueContext.queueCommand(new LoginAnonymous(login).toBuffer());

        while (!Thread.interrupted()) {
            //Helpers.printKeys(selector); // for debug
            //System.out.println("Starting select");
            try {
                selector.select(this::treatKey);
                if(firstSend){
                    firstSend = false;
                    uniqueContext.queueCommand(new LoginAnonymous(login).toBuffer());
                }
                processInstruction();
            } catch (UncheckedIOException tunneled) {
                console.interrupt();
                Thread.currentThread().interrupt();
                throw tunneled.getCause();
            }
            //System.out.println("Select finished");
        }
    }

    private void treatKey(SelectionKey key) {
        // TODO review the treatment of exceptions from method doConnect, doRead, doWrite
        try {
            if (key.isValid() && key.isConnectable()) {
                uniqueContext.doConnect();
            }
            if (key.isValid() && key.isWritable()) {
                uniqueContext.doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                uniqueContext.doRead();
            }
        } catch (IOException ioe) {
            // lambda call in select requires to tunnel IOException
            //logger.warning(serverAddress.getHostString() + ":" + serverAddress.getPort() + " is unreachable");
            //silentlyClose(key);
            //console.interrupt();
            //Thread.currentThread().interrupt();
            throw new UncheckedIOException(ioe);
        }
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
    //                                           client commands processing                                           //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Integer hashFileInfo(FilePrivate fp){
        return Objects.hash(fp.serverSrc(), fp.loginSrc(), fp.fileName());
    }

    private void writeFilePiece(FilePrivate fp){
        Path filepath = Path.of(directory, fp.fileName());
        try{
            var file = Files.write(filepath, fp.bytes(),  StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }catch(IOException ioe){
            logger.info("could not write file piece because:" + ioe + "\n\tpath = "+filepath);
        }
        return;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                   Context: represents the state of a discussion with a specific interlocutor                   //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static private class Context {
        private final SelectionKey key;
        private final SocketChannel sc;
        private final ClientChatFusion client;
        private final ByteBuffer bufferIn = ByteBuffer.allocate(BUFFER_SIZE);
        private final ByteBuffer bufferOut = ByteBuffer.allocate(BUFFER_SIZE);
        private final ArrayDeque<ByteBuffer> queueCommand = new ArrayDeque<>();
        private final Map<Integer, Integer> fileTransferProgress = new HashMap<>();
        private boolean closed = false;
        private Writer writer = null;
        private AuthenticationState authenticationState = AuthenticationState.UNREGISTERED;

        private final FrameReader frameReader = new FrameReader();
        private FrameVisitor frameVisitor;


        private Context(SelectionKey key, ClientChatFusion client) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            this.client = client;
            frameVisitor = new UnregisteredVisitor(this, client);
        }

        /**
         * Process the content of bufferIn
         * <p>
         * The convention is that bufferIn is in write-mode before the call to process
         * and after the call
         */
        private void processIn() {
            for (; ; ) {
                var status = frameReader.process(bufferIn);
                switch(status){
                    case ERROR:{
                        silentlyClose();
                        return;
                    }
                    case REFILL:{
                        return;
                    }
                    case DONE:{
                        Frame frame = frameReader.get();
                        frameReader.reset();
                        treatFrame(frame);
                    }
                }
            }
        }

        private void treatFrame(Frame frame){
            frame.accept(frameVisitor);
        }

        /**
         * Add a command to the command queue, tries to fill bufferOut and updateInterestOps
         *
         * @param cmd - cmd
         */
        private void queueCommand(ByteBuffer cmd) {
            queueCommand.addLast(cmd);
            processOut();
            updateInterestOps();
        }

        /**
         * Try to fill bufferOut from the message queue
         */
        private void processOut() {
            //System.out.println("processOut");
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
         * @throws IOException - If an I/O error occurs
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
         * @throws IOException - If an I/O error occurs
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
            key.interestOps(SelectionKey.OP_READ); // OP_WRITE to send the LoginAnonymous command ?
        }

        private enum AuthenticationState {
            UNREGISTERED, LOGGED
        }
    }

    private static class FileSender {
        private static final Deque<FileSendInfo> fileQueue = new ArrayDeque<>(10);
        private static ClientChatFusion client;
        private final static int TIMEOUT_STD = 100;
        private final static int MAX_SLEEP_TIME = 3000;
        private final static int COMMAND_QUEUE_FULL_TIMEOUT = 200;

        // methods:
        public boolean sendNewFile(FileSendInfo fileSendInfo){
            synchronized (fileQueue){
                return fileQueue.offer(fileSendInfo);
            }
        }

        private static void senderAction() {
            int sleepTime = TIMEOUT_STD;
            for(;;){
                try {
                        if (fileQueue.isEmpty() && sleepTime < MAX_SLEEP_TIME) {
                            sleepTime += sleepTime * 2;
                        }
                        if (!fileQueue.isEmpty()) {
                            sleepTime = TIMEOUT_STD;
                            var fileInfo = fileQueue.peekFirst();
                            client.uniqueContext.queueCommand(fileInfo.buildFileChunk(client));
                            if(fileInfo.isFullySent()){
                                System.out.println(client.uniqueContext.queueCommand);
                                fileQueue.removeFirst();
                            }
                            client.selector.wakeup();
                        }
                    } catch (IOException ioe){
                    throw new UncheckedIOException(ioe);
                }
                try {
                    Thread.sleep(sleepTime);
                }catch (InterruptedException ie){
                    return;
                }
            }
        }

        public FileSender(ClientChatFusion clientInput)throws IOException{
            client = clientInput;
            try {
                new Thread(FileSender::senderAction).start();
            }catch (UncheckedIOException uioe){
                throw uioe.getCause();
            }
        }
    }

    /**
     * The classe storing any file, chunking and file sending process.
     */
    private static class FileSendInfo {
        private final FileChannel file;
        private final String loginDst;
        private final String fileName;
        private final String serverDst;
        private static final int MAX_IN_COMMAND = 5000;
        private final int nbChunk;
        private int nbChunkSent;
        private ByteBuffer chunk = ByteBuffer.allocate(MAX_IN_COMMAND);

        /**
         * <b>Constructor</b>
         * @param filePath the path to the file.
         * @param loginDst the destination client
         * @param serverDst the destination server
         * @param fileName the name of the file
         * @throws IOException should the file be inaccessible or unreadable.
         */
        public FileSendInfo(String filePath, String loginDst, String serverDst, String fileName) throws IOException {
            Path path = Path.of(filePath);
            this.file  = FileChannel.open(path);
            this.nbChunk = (Math.toIntExact(file.size()) / MAX_IN_COMMAND) + ((file.size()%MAX_IN_COMMAND == 0)?0:1);
            this.fileName = fileName;
            this.loginDst = loginDst;
            this.serverDst = serverDst;
            this.nbChunkSent = 0;
        }

        /**
         * checks if all chunk were sent
         * @return T/F answering the question.
         * @throws IOException
         */
        public boolean isFullySent() throws IOException {
            if(nbChunk == nbChunkSent){
                file.close();
                System.out.println("Just finished sending a file");
            }
            return nbChunkSent == nbChunk;
        }

        /**
         * Builds a chunk froim the file
         * @param client a hook on the ChatFudion client that crated this process.
         * @return a byte buffer filled according to the RFC.
         * @throws IOException
         */
        public ByteBuffer buildFileChunk(ClientChatFusion client) throws IOException {
            chunk.clear();
            var readValue = 1;
            while(chunk.hasRemaining()){
                var nbBytesRead = file.read(chunk);
                System.out.println("nbBytesRead : " + nbBytesRead);
                if(nbBytesRead <= 0){
                    break;
                }
            }
            nbChunkSent++;
            chunk.flip(); // -> Sets position & limit in order to have an exact array and not full of trailing 0.
            var data = Arrays.copyOf(chunk.array(), chunk.limit());
            System.out.println("data arrya size = " + data.length);
            chunk.flip();
            return client.buildFileChunk(data, serverDst, loginDst, fileName, nbChunk);
        }
    }
    /**
     * The class implementing the visitor.
     */
    private static class UnregisteredVisitor implements  FrameVisitor {
        private final Context context;
        private final ClientChatFusion client;

        public UnregisteredVisitor(Context context, ClientChatFusion client){
            this.context = context;
            this.client = client;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            client.eject();
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            client.eject();
        }

        @Override
        public void visit(FusionInit fusionInit) {
            client.eject();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            client.eject();
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            client.eject();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            client.eject();
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            client.eject();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            client.eject();
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            client.serverName = loginAccepted.serverName();
            System.out.println("Authentication to " + client.serverName + " success with login : " + client.login);
            client.console.start();
            context.authenticationState = Context.AuthenticationState.LOGGED;
            context.frameVisitor = new LoggedVisitor(context, client);
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            client.eject();
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            System.out.println("Username already used, please re-start and chose another one");
            //console.interrupt(); // not need because console was not started yet at this point
            context.silentlyClose();
            Thread.currentThread().interrupt(); // we are in main thread, so this closes the main program.
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            client.eject();
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            client.eject();
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            client.eject();
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            client.eject();
        }
    }
    private static class LoggedVisitor implements FrameVisitor {
        private final Context context;
        private final ClientChatFusion client;

        public LoggedVisitor(Context context, ClientChatFusion client){
            this.context = context;
            this.client = client;
        }

        @Override
        public void visit(FilePrivate filePrivate) {
            var fileId = client.hashFileInfo(filePrivate);
            client.writeFilePiece(filePrivate);
            context.fileTransferProgress.merge(fileId, 1, Integer::sum);
            if (context.fileTransferProgress.get(fileId) == filePrivate.nbBlocks()){
                System.out.println("received file: " + filePrivate.fileName()+ " can be found in folder " + client.directory);
            }
        }

        @Override
        public void visit(FusionInexistantServer fusionInexistantServer) {
            client.eject();
        }

        @Override
        public void visit(FusionInit fusionInit) {
            client.eject();
        }

        @Override
        public void visit(FusionInvalidName fusionInvalidName) {
            client.eject();
        }

        @Override
        public void visit(FusionRegisterServer fusionRegisterServer) {
            client.eject();
        }

        @Override
        public void visit(FusionRootTableAsk fusionRootTableAsk) {
            client.eject();
        }

        @Override
        public void visit(FusionRouteTableSend fusionRouteTableSend) {
            client.eject();
        }

        @Override
        public void visit(FusionTableRouteResult fusionTableRouteResult) {
            client.eject();
        }

        @Override
        public void visit(LoginAccepted loginAccepted) {
            client.eject();
        }

        @Override
        public void visit(LoginAnonymous loginAnonymous) {
            client.eject();
        }

        @Override
        public void visit(LoginRefused loginRefused) {
            client.eject();
        }

        @Override
        public void visit(MessagePrivate messagePrivate) {
            System.out.println(messagePrivate.loginSrc() + "@" + messagePrivate.serverSrc() + " : " + messagePrivate.msg());
        }

        @Override
        public void visit(MessagePublicSend messagePublicSend) {
            client.eject();
        }

        @Override
        public void visit(MessagePublicTransmit messagePublicTransmit) {
            System.out.println(messagePublicTransmit.login() + "@" + messagePublicTransmit.server() + " PUBLIC: " + messagePublicTransmit.msg());
        }

        @Override
        public void visit(ServerConnexion serverConnexion) {
            client.eject();
        }
    }
}