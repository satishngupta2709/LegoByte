package org.example.core;


import org.example.config.Configuration;

import java.util.logging.Logger;



//TODO: make the eviction strategy configuration driven
//TODO: support multiple eviction strategies
public class Eviction {

    private  static final Logger logger= Logger.getLogger(Store.class.getName());


   /*
    * this method will evict all keys randomly
    */
    private static void evictAllKeysRandom(){
         long evictionCount = (long) (Configuration.EvictionRatio*Configuration.KeyLimit);
         //it will pick random keys from the store and evict them as hashmap iternation in java is random
         for(var key : Store.snapshot().keySet()){
            Store.Del(key);
            evictionCount--;
            if(evictionCount<=0){
                break;
            }
         }
    }


    private static void evictFirst(){

        for(String key : Store.snapshot().keySet() ){
            Store.Del(key);
            break;
        }
        logger.info("Eviction called "+Store.size());
    }




    public static void evict(){
        //TODO: make it efficient by doing thorough sampling
       

        switch (Configuration.EvictionStrategy){
            case "simple-first":
                evictFirst();
                break;
            case "allkeys-random":
                evictAllKeysRandom();
                break;

        }
    }
}
