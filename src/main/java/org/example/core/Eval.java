package org.example.core;

import org.example.model.LegoByteCmd;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static org.example.core.Resp.encode;

public class Eval {

    public static void evalAndRespond(LegoByteCmd cmd, Socket connection) throws Exception {
        switch (cmd.Cmd()) {
            case "PING":
                evalPING(cmd.Args(), connection);
                break;
            default:
                evalPING(cmd.Args(), connection); // Default to PING for unknown commands
                break;
        }
    }

    public static void evalPING(String[] args,Socket connection) throws Exception{
        byte[] b;
        if(args.length>=2){
            throw new Exception("ERR wrong number of arguments for 'ping' command");
        }
        if(args.length==0){
            b=encode("PONG",true);
        }
        else {
            b=encode(args[0],false);
        }

        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(b);
        outputStream.flush();
    }
}
