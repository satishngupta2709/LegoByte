package org.example.config;

public class Configuration {




    public static String Host="0.0.0.0";
    public static int Port = 2709;
    public static int KeyLimit = 500;
    public static float EvictionRatio= 0.40F;


    /*

    Eviction Stategy
    1. simple-first
    2. allkey-random


     */

    public static String EvictionStrategy ="allkey-random";

    public static String AOFFile="./legobyte.aof";
}
