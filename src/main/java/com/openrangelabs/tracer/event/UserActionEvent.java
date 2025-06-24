package com.openrangelabs.tracer.event;

import com.openrangelabs.tracer.model.UserAction;
import org.springframework.context.ApplicationEvent;

public class UserActionEvent extends ApplicationEvent {

    private final UserAction userAction;

    public UserActionEvent(Object source, UserAction userAction) {
        super(source);
        this.userAction = userAction;
    }

    public UserAction getUserAction() {
        return userAction;
    }
}