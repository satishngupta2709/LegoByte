package org.example.model;

public enum Encoding {
    RAW, // Raw byte/string representation
    INT, // Inlined integer encoding like Redis's embstr/int
    ZIPLIST; // Ziplist encoding for lists and hashes
}
