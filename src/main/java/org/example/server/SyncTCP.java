package org.example.server;

import org.example.Main;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

public class SyncTCP {
    private static int concurrent_connection=0;
    private  static final Logger logger= Logger.getLogger(SyncTCP.class.getName());


    public static void RunSyncTCPServer(String host , int port){
        logger.info("Starting a synchronous TCP server on,"+host +":"+port);
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true){
                logger.info("inwhile");
                Socket clientSocket = serverSocket.accept();
                concurrent_connection+=1;
                logger.info("Client connected with address: "+clientSocket.getInetAddress() + "concurrent client connection: "+concurrent_connection);
                while (true){
                    try {
                        String cmd=readCommand(clientSocket);
                        if (cmd=="exit()"){
                            logger.info("closing");
                            break;
                        }
                        logger.info("command "+ cmd);
                        respond(cmd,clientSocket);

                    }catch (Exception e){
                        clientSocket.close();
                        concurrent_connection-=1;
                        logger.info("client disconnected , "+clientSocket.getInetAddress()+" concurrent connection"+concurrent_connection);
                    }
                }
            }


        }catch (IOException e){
            logger.warning("Error occure while creating Socket Server");
        }


    }

    public static String readCommand(Socket connection) throws IOException {
        InputStream inputStream = connection.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line = reader.readLine(); // reads until '\n' or EOF
        if (line == null) {
            return ""; // EOF reached (e.g., Ctrl+C)
        }
        return line;
    }
    public static void respond(String cmd, Socket connection) throws IOException {
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(cmd.getBytes());
        outputStream.flush();
    }
}
