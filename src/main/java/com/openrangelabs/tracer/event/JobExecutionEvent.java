package com.openrangelabs.tracer.event;

import com.openrangelabs.tracer.model.JobExecution;
import org.springframework.context.ApplicationEvent;

public class JobExecutionEvent extends ApplicationEvent {

    private final JobExecution jobExecution;

    public JobExecutionEvent(Object source, JobExecution jobExecution) {
        super(source);
        this.jobExecution = jobExecution;
    }

    public JobExecution getJobExecution() {
        return jobExecution;
    }
}