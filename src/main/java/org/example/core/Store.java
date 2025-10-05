package org.example.core;

import org.example.config.Configuration;
import org.example.model.ObjectStore;
import org.example.server.AsyncTCP;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.example.config.Configuration;

public class Store {
    private  static final Logger logger= Logger.getLogger(Store.class.getName());
    private static Map<String, ObjectStore> store = new HashMap<>();

    public static Map<String, ObjectStore> snapshot(){
        return new HashMap<>(store);
    }

    public static void Put(String key,ObjectStore obj){
        if(store.size()>= Configuration.KeyLimit){
            Eviction.evict();
        }
        store.put(key,obj);
    }
    public static ObjectStore Get(String key){
        ObjectStore obj = store.get(key);
        if(obj!=null){
            if(obj.isExpired()){
                store.remove(key);
                return null;

            }
        }
        return obj;
    }
    public static boolean Del(String key){
        try {
            ObjectStore r= store.remove(key);
            return true;
        }catch (Exception e){
            return false;
        }


    }

    public static double expireSample(){
        int limit= 10;
        int expiredCount=0;

        for(String key : store.keySet() ){
            ObjectStore obj = store.get(key);
            if(obj.getExpiresAt()!=1){
                limit--;
                if(obj.isExpired()){
                    store.remove(key);
                    expiredCount++;
                }
            }

            if(limit==0){
                break;
            }

        }
        logger.info("Deleted the expired key : "+expiredCount + "total key "+store.size());
        return (double) expiredCount /20;
    }

    public static void evictFirst(){

        for(String key : store.keySet() ){
            store.remove(key);
            break;
        }
        logger.info("Eviction called "+store.size());
    }




}
