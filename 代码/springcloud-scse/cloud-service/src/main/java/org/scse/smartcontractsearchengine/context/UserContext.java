package org.scse.smartcontractsearchengine.context;


public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId){
        USER_ID.set(userId);
    }

    public static Long getUserId(){
        return USER_ID.get();
    }

    public static void remove(){
        USER_ID.remove();
    }
}
