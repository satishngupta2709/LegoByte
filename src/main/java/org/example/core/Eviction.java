package org.example.core;




//TODO: make the eviction strategy configuration driven
//TODO: support multiple eviction strategies
public class Eviction {

    public static void evict(){
        //TODO: make it efficient by doing thorough sampling
        Store.evictFirst();
    }
}
