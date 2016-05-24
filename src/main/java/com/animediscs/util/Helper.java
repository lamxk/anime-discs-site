package com.animediscs.util;

import org.apache.logging.log4j.*;

import java.util.function.Function;

public abstract class Helper {

    private static Logger logger = LogManager.getLogger(Helper.class);

    public static void waitFor(Object object) {
        synchronized (object) {
            try {
                object.wait(1000);
            } catch (InterruptedException e) {
                logger.catching(Level.WARN, e);
            }
        }
    }

    public static void nodify(Object object) {
        synchronized (object) {
            object.notify();
        }
    }

    public static <T, R> R nullSafeGet(T t, Function<T, R> mapper) {
        return t == null ? null : mapper.apply(t);
    }

}