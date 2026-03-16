package org.example.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A simplified Ziplist implementation backed by a byte array.
 * Each entry is currently encoded as: [length of string (4 bytes)] [string
 * bytes]
 * In a real Redis ziplist, it supports multiple encodings (int, string) and
 * length headers
 * to traverse forwards and backwards. This is a simplified
 * forward-traversal-focused version
 * tailored for basic LPUSH/LRANGE operations.
 */
public class Ziplist {
    private byte[] data;
    // head points to the start of the first element.
    // Since we only do LPUSH, we might need a way to prepend or maintain a list.
    // For simplicity, we can just allocate a byte array and "prepend" to it by
    // creating a new array.

    // To implement LPUSH efficiently-ish for a simple representation:
    // When LPUSHing, we can create a new byte array: [new element length][new
    // element bytes][old ziplist bytes]

    public Ziplist() {
        this.data = new byte[0];
    }

    public int lpush(String value) {
        byte[] strBytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] lenBytes = intToBytes(strBytes.length);

        byte[] newData = new byte[lenBytes.length + strBytes.length + this.data.length];

        // Copy new length
        System.arraycopy(lenBytes, 0, newData, 0, lenBytes.length);
        // Copy new string bytes
        System.arraycopy(strBytes, 0, newData, lenBytes.length, strBytes.length);
        // Copy old data
        System.arraycopy(this.data, 0, newData, lenBytes.length + strBytes.length, this.data.length);

        this.data = newData;

        // Return length of the list (number of elements). We have to count them or keep
        // track.
        return countElements();
    }

    public List<String> getAll() {
        List<String> elements = new ArrayList<>();
        int offset = 0;

        while (offset < this.data.length) {
            int len = bytesToInt(this.data, offset);
            offset += 4;
            String str = new String(this.data, offset, len, StandardCharsets.UTF_8);
            offset += len;
            elements.add(str);
        }

        return elements;
    }

    public List<String> lrange(int start, int stop) {
        int length = countElements();
        
        // Handle negative indices
        if (start < 0) start = length + start;
        if (stop < 0) stop = length + stop;
        
        // Bound checks
        if (start < 0) start = 0;
        if (stop >= length) stop = length - 1;
        
        List<String> result = new ArrayList<>();
        if (start > stop || start >= length) {
            return result;
        }
        
        int offset = 0;
        int currentIndex = 0;
        
        while (offset < this.data.length && currentIndex <= stop) {
            int len = bytesToInt(this.data, offset);
            offset += 4;
            
            if (currentIndex >= start) {
                String str = new String(this.data, offset, len, StandardCharsets.UTF_8);
                result.add(str);
            }
            
            offset += len;
            currentIndex++;
        }
        
        return result;
    }

    private int countElements() {
        int count = 0;
        int offset = 0;
        while (offset < this.data.length) {
            int len = bytesToInt(this.data, offset);
            offset += 4 + len;
            count++;
        }
        return count;
    }

    private byte[] intToBytes(int current) {
        return new byte[] {
                (byte) (current >> 24),
                (byte) (current >> 16),
                (byte) (current >> 8),
                (byte) current
        };
    }

    private int bytesToInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24) |
                ((b[offset + 1] & 0xFF) << 16) |
                ((b[offset + 2] & 0xFF) << 8) |
                (b[offset + 3] & 0xFF);
    }
}
