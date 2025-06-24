package com.openrangelabs.tracer.model.converters;

import com.openrangelabs.tracer.util.UuidConverter;

import java.util.UUID;

public class MongoUuidConverter implements UuidConverter {
    public Object convertToDatabase(UUID uuid) {
        return uuid; // MongoDB driver handles conversion
    }

    public UUID convertFromDatabase(Object dbValue) {
        return (UUID) dbValue;
    }

    public String getColumnDefinition() {
        return "BinData"; // Not used in MongoDB
    }
}
