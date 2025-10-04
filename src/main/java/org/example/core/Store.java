package org.example.core;

import org.example.model.ObjectStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private static Map<String, ObjectStore> store = new HashMap<>();

    public static void Put(String key,ObjectStore obj){
        store.put(key,obj);
    }
    public static ObjectStore Get(String key){
        return store.get(key);
    }
    public static boolean Del(String key){
        try {
            ObjectStore r= store.remove(key);
            return true;
        }catch (Exception e){
            return false;
        }


    }

}
