package org.example.core;

import org.example.model.LegoByteCmd;
import org.example.model.ObjectStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;

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

    public static byte[] evalToBytes(LegoByteCmd cmd) throws Exception {
        switch (cmd.Cmd()){
            case "PING":
                return evalPingToBytes(cmd.Args());

            case "SET":
                return  evalSETToBytes(cmd.Args());

            case "GET":
                return evalGETToBytes(cmd.Args());
            case "TTL":
                return evalTTLToByte(cmd.Args());
            default:
                return evalPingToBytes(cmd.Args());
        }
    }

    private static byte[] evalTTLToByte(String[] args) throws Exception {
        if(args.length!=1){
            throw new Exception("ERR wrong number of arguments for 'ttl' command");
        }
        String key = args[0];
        ObjectStore obj = Store.Get(key);

        if (obj == null){
            return Resp.encode("Nil",false);
        }
        if(obj.getExpiresAt()==-1){
            return Resp.encode("-1",false);
        }
        long durationMs= obj.getExpiresAt()- Instant.now().toEpochMilli();

        if(durationMs<0){
            return  Resp.encode("-2",false);
        }
        String d= String.valueOf(durationMs/1000);
        return Resp.encode(d,false);
    }

    private static byte[] evalGETToBytes(String[] args)  throws  Exception{
        if(args.length!=1){
            throw new Exception("ERR wrong number of arguments for 'get' command");
        }
        String key = args[0];
        ObjectStore data = Store.Get(key);
        if (data == null){
            return Resp.encode("Nil",false);

        }
        if (data.isExpired()){
            return  Resp.encode("Nil",false);
        }

        return Resp.encode(data.getValue(),false);
    }

    private static byte[] evalSETToBytes(String[] args) throws Exception {
        if(args.length <=1){
            throw  new Exception("ERR wrong number of arguments for 'set' command");
        }
        String key= args[0];
        String value =args[1];
        int exDurationMs=-1;

        for(int i=2;i<args.length;i++){
            String arg=args[i];
            switch (arg){
                case "EX":
                case "ex":
                  i++;
                  if(i==args.length){
                      throw new Exception("ERR syntax error");
                  }
                  try {
                      exDurationMs = Integer.parseInt(args[3]);
                  }catch (Exception e){
                      throw e;
                  }
                  exDurationMs=exDurationMs*1000;
                  break;
                default:
                    throw new Exception("ERR syntax error");

            }
        }
        var data = new ObjectStore<String>(value,exDurationMs);
        Store.Put(key,data);
        return Resp.encode("OK",false);
    }

    public static byte[] evalPingToBytes(String[] args) throws Exception {
        if (args.length >= 2){
            throw new Exception("ERR wrong number of arguments for 'ping' command");
        }
        if (args.length == 0){
            return Resp.encode("PONG", true);
        } else {
            return Resp.encode(args[0], false);
        }
    }
}
