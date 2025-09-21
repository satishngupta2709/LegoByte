package org.example;

import org.example.core.Resp;
import org.example.server.SyncTCP;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

public class Main {
    private static String host="0.0.0.0";
    private static int port =2709;
    private  static final Logger logger= Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        setupFlags(args);
        logger.info("Building the lego ...");
        SyncTCP.RunSyncTCPServer(host,port);
    }


    private static void setupFlags(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        host = args[++i];
                    } else {
                        logger.warning("No value provided for --host, using default: " + host);
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            logger.warning("Invalid port number, using default: " + port);
                        }
                    } else {
                        logger.warning("No value provided for --port, using default: " + port);
                    }
                    break;
                default:
                    logger.warning("Unknown argument: " + args[i]);
            }
        }
    }
}