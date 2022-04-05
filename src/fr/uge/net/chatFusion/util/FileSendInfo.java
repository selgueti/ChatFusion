package fr.uge.net.chatFusion.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileSendInfo {
    private final BufferedReader file;
    private final String loginDst;
    private final String serverDst;
    private int progress = 0;
    private static final int MAX_IN_COMMAND = 5000;
    private final int nbChunk;
    private char[] charBuff = new char[MAX_IN_COMMAND];

    public FileSendInfo(String filePath, String loginDst, String serverDst) throws IOException {
        file  = Files.newBufferedReader(Path.of(filePath), StandardCharsets.US_ASCII);
        nbChunk = Math.toIntExact(Files.size(Path.of(filePath)) / MAX_IN_COMMAND);
        this.loginDst = loginDst;
        this.serverDst = serverDst;
    }

    public byte[] readChunk() throws IOException {
        if(file.read(charBuff, MAX_IN_COMMAND * progress, MAX_IN_COMMAND * (progress + 1)) != -1){
            var test = StandardCharsets.US_ASCII.encode(CharBuffer.wrap(charBuff));

            file.read()
        }
    }
}
