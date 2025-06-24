package com.openrangelabs.tracer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceUserAction {
    /**
     * The operation name for metrics. If empty, uses method name.
     */
    String value() default "";

    boolean includeArgs() default false;

    boolean includeResult() default false;

    /**
     * Whether to log slow operations (above threshold)
     */
    boolean logSlowOperations() default true;

    /**
     * Threshold in milliseconds for what constitutes a slow operation
     */
    long slowThresholdMs() default 1000;

    /**
     * Whether to include memory usage measurements
     */
    boolean measureMemory() default false;

    /**
     * Whether to sample the operation (useful for high-frequency methods)
     */
    boolean sample() default false;

    /**
     * Sample rate (0.0 to 1.0) when sampling is enabled
     */
    double sampleRate() default 0.1;
}
