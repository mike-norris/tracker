package com.openrangelabs.tracer.model.converters;

import com.openrangelabs.tracer.util.UuidConverter;

import java.util.UUID;

public class PostgresUuidConverter implements UuidConverter {
    public Object convertToDatabase(UUID uuid) {
        return uuid; // Direct storage
    }

    public UUID convertFromDatabase(Object dbValue) {
        return (UUID) dbValue;
    }

    public String getColumnDefinition() {
        return "UUID";
    }
}
