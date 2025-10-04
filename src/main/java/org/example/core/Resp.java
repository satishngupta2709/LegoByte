package org.example.core;

import org.example.model.DecodeResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Resp {

    public static List<String> decodeArrayString(byte[] data) throws Exception {
        var value = decode(data);
        if(value instanceof String){
            return  Arrays.stream(((String) value).split(" ")).toList();
        }

        List<String> res = new ArrayList<>();

        if (! (value instanceof ArrayList<?>)){

            res.add(value.toString());
            return res;
        } else {

            for(var e : ((ArrayList<?>) value).toArray()){
                res.add((String) e);
            }


        }
        return res;
    }



    public static <T> T decode(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            throw new Exception("no data");
        }
        DecodeResult<T> result = decodeOne(data);
        return result.getValue();
    }

    public static <T>  DecodeResult<T> decodeOne(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            throw new Exception("no data");
        }

        switch (data[0]) {
            case '+':
                return readSimpleString(data);
            case '-':
                return readError(data);
            case ':':
                return readInt64(data);
            case '$':
                return readBulkString(data);
            case '*':
                return readArray(data);
            default:
                return new DecodeResult<>(null,0);
        }
    }




    /**
     * Decodes a RESP Simple String from the given byte array.
     *
     * <p>A Simple String in RESP starts with '+' and ends with CRLF ("\r\n").</p>
     *
     * @param data the raw RESP byte array
     * @return a {@link DecodeResult} containing the decoded string value and the number of bytes read
     * @throws IllegalArgumentException if the input format is invalid
     */
    public static <T> DecodeResult<T> readSimpleString(byte[] data) throws Exception {

        if (data[0] != '+') {
            throw new Exception("Invalid simple string format");
        }
        int pos = 1;

        // Find the carriage return position
        while (pos < data.length && data[pos] != '\r') {
            pos++;
        }
        // Convert bytes to string (assuming UTF-8 encoding)
        String result = new String(data, 1, pos - 1, StandardCharsets.UTF_8);
        int bytesRead = pos + 2; // +2 for CRLF

        return (DecodeResult<T>) new DecodeResult<String>(result,bytesRead);
    }

    public  static <T>  DecodeResult<T> readInt64(byte[] data) throws Exception{
        // first character
        int pos=1;
        int value=0;

        while (pos<data.length && data[pos]!='\r'){
            value=value*10 + (data[pos]-'0');
            pos+=1;
        }
        int bytesRead=pos+2;

        return (DecodeResult<T>) new DecodeResult<Integer>(value,bytesRead);
    }
    public static <T> DecodeResult<T> readBulkString(byte[] data) throws Exception{
        int pos=1;
        var res= readLength(data,pos);
        int len= res.getValue();
        int delta= res.getDelta();
        pos+=delta;

        String result = new String(data, pos-1,  len, StandardCharsets.UTF_8);
        int bytesRead = pos + len + 2; // +2 for CRLF

        return (DecodeResult<T>) new DecodeResult<>(result,bytesRead);


    }

    private static DecodeResult<Integer> readLength(byte[] data, int pos) {
        int length = 0;

        while (pos < data.length) {
            byte b = data[pos];

            if (b >= '0' && b <= '9') {
                length = length * 10 + (b - '0');
                pos++;
            } else {
                // Expect CRLF
                if (pos + 1 < data.length && data[pos] == '\r' && data[pos + 1] == '\n') {
                    return new DecodeResult<>(length, pos + 2); // length + bytes read
                }
                return new DecodeResult<>(0, 0); // invalid format
            }
        }

        return new DecodeResult<>(0, 0); // incomplete data
    }

    public static <T> DecodeResult<T> readArray(byte[] data) throws Exception {
        int pos=1;
        DecodeResult<Integer> res = readLength(data,pos);
        int count= res.getValue();
        int delta= res.getDelta();

        pos+=delta;
        List<T> elements = new ArrayList<T>();
        for(int i=0;i<count;i++){
            DecodeResult<T> decodeResult = decodeOne(Arrays.copyOfRange(data, pos-1, data.length));
            elements.add(decodeResult.getValue());
            pos+=decodeResult.getDelta()-1;
        }
        return (DecodeResult<T>) new DecodeResult<>(elements,pos);
    }

    public static <T>  DecodeResult<T> readError(byte[] data) throws Exception {
        return readSimpleString(data);
    }

    public static byte[] encode(Object value,boolean isSimple){

        if(value instanceof String){
            if(isSimple){
                String s = "+"+ value.toString()+"\r\n";
                return s.getBytes();
            }
            String s = "$"+ value.toString().length() +"\r\n" + value.toString() +"\r\n";
            return s.getBytes();
        }
        return new byte[]{};
    }
}
