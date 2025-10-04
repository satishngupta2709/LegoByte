package org.example.core;

import org.example.model.ObjectStore;
import org.example.server.AsyncTCP;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Store {
    private  static final Logger logger= Logger.getLogger(Store.class.getName());
    private static Map<String, ObjectStore> store = new HashMap<>();

    public static void Put(String key,ObjectStore obj){
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
        logger.info("Deleted the expired key : "+expiredCount);
        return (double) expiredCount /20;
    }

}
