package org.example.core;

import static org.example.core.Store.expireSample;

public class Expire {

    public static void DeleteExpiredKey(){
        while (true){
            double fraction= expireSample();
            if(fraction<0.25){
                break;
            }
        }

    }


}
