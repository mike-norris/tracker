package com.openrangelabs.tracer.util;

import java.util.UUID;

public interface UuidConverter {
    Object convertToDatabase(UUID uuid);
    UUID convertFromDatabase(Object dbValue);
    String getColumnDefinition();
}
