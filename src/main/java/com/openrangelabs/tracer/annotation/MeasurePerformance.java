package com.openrangelabs.tracer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MeasurePerformance {
    String value() default "";
    boolean sample() default false;
    double sampleRate() default 0.1;
    boolean measureMemory() default false;
    boolean logSlowOperations() default true;
    long slowThresholdMs() default 1000;
}
