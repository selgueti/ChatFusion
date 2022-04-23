package fr.uge.net.chatFusion.util;

//import fr.uge.net.chatFusion.clientChatFusion.ClientChatFusion;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSendInfo {
    private final BufferedReader file;
    private final String loginDst;
    private final String fileName;
    private final String serverDst;
    private int progress = 0;
    private static final int MAX_IN_COMMAND = 5000;
    private final int nbChunk;
    private char[] charBuff = new char[MAX_IN_COMMAND];

    public FileSendInfo(String filePath, String loginDst, String serverDst, String fileName) throws IOException {
        file  = Files.newBufferedReader(Path.of(filePath), StandardCharsets.US_ASCII);
        nbChunk = Math.toIntExact(Files.size(Path.of(filePath)) / MAX_IN_COMMAND);
        this.fileName = fileName;
        this.loginDst = loginDst;
        this.serverDst = serverDst;
    }

    /*
    public ByteBuffer buildFieChunk(ClientChatFusion client) throws IOException {
        var data = readChunkIntoCommand();
        return client.buildFileChunk(data, serverDst, loginDst, fileName, nbChunk);
    }*/

    public byte[] readChunkIntoCommand() throws IOException {
        var  limit = 0;
        var nbByteRead = file.read(charBuff,progress * MAX_IN_COMMAND, MAX_IN_COMMAND);
        while(nbByteRead < MAX_IN_COMMAND && nbByteRead != -1){
            limit += nbByteRead;
            nbByteRead = file.read(charBuff, limit, (MAX_IN_COMMAND- 1) - limit);
        }
        progress+=1;
        return StandardCharsets.US_ASCII.encode(CharBuffer.wrap(charBuff).limit(limit)).array();
    }
}
