package com.openrangelabs.tracer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceUserAction {
    String value() default "";
    String category() default "USER_ACTION";
    boolean includeArgs() default false;
    boolean includeResult() default false;
    boolean measureTiming() default true;
    boolean async() default true;
}
