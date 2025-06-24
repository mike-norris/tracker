package com.openrangelabs.tracer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceJob {
    String jobType() default "GENERAL";
    String jobName() default "";
    String queueName() default "default";
    int priority() default 5;
    boolean includeInputData() default true;
    boolean includeOutputData() default true;
    boolean measureResources() default false;
}
