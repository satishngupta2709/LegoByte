package org.example.server;


import org.example.core.Eval;
import org.example.model.LegoByteCmd;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import java.util.List;
import java.util.logging.Logger;

import static org.example.core.Resp.decodeArrayString;

public class SyncTCP {
    private static int concurrent_connection=0;
    private  static final Logger logger= Logger.getLogger(SyncTCP.class.getName());


    public static void RunSyncTCPServer(String host , int port){
        logger.info("Starting a synchronous TCP server on,"+host +":"+port);
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true){
                logger.info("in while");
                final Socket clientSocket = serverSocket.accept();
                concurrent_connection+=1;
                logger.info("Client connected with address: "+clientSocket.getInetAddress() + " concurrent client connection: "+concurrent_connection);
                while (true){
                    try {
                        LegoByteCmd cmd=readCommand(clientSocket);
                        logger.info("command "+ cmd.Cmd());
                        respond(cmd,clientSocket);

                    }catch (Exception e){
                        respondError(e,clientSocket);
                        clientSocket.close();
                        concurrent_connection-=1;
                        logger.info("client disconnected , "+clientSocket.getInetAddress()+" concurrent connection: "+concurrent_connection);
                        break;
                    }
                }
            }


        }catch (IOException e){
            logger.warning("Error occure while creating Socket Server");
        }


    }

    public static LegoByteCmd   readCommand(Socket connection) throws Exception {
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int b;
        // Read until newline (0x0A)
        while ((b = inputStream.read()) != -1) {
            if (b == '\n') {
                break; // End of command
            }
            buffer.write(b);
        }

        // If connection closed before any data
        if (buffer.size() == 0 && b == -1) {
            throw new EOFException("Stream closed before command received");
        }

        byte[] actualData = buffer.toByteArray();

        List<String> tokens =decodeArrayString(actualData);


        if (tokens.isEmpty()) {
            throw new IOException("Empty command received");
        }

        String[] arg = tokens.subList(1, tokens.size()).toArray(new String[0]);
        return new LegoByteCmd(tokens.get(0).toUpperCase(), arg);
    }


    public static void respond(LegoByteCmd cmd, Socket connection) throws IOException {
        try {
            Eval.evalAndRespond(cmd,connection);
        }catch (Exception e){
            respondError(e,connection);
        }

    }

    public  static void respondError(Exception e, Socket connection) throws IOException {
        OutputStream outputStream = connection.getOutputStream();
        String err= "-"+e.getMessage()+"\r\n";
        outputStream.write(err.getBytes());
        outputStream.flush();
    }
}
