package com.openrangelabs.tracer.model.converters;

import com.openrangelabs.tracer.util.UuidConverter;

import java.nio.ByteBuffer;
import java.util.UUID;

public class MysqlUuidConverter implements UuidConverter {
    public Object convertToDatabase(UUID uuid) {
        if (uuid == null) return null;

        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public UUID convertFromDatabase(Object dbValue) {
        if (dbValue == null) return null;

        byte[] bytes = (byte[]) dbValue;
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long mostSigBits = bb.getLong();
        long leastSigBits = bb.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    public String getColumnDefinition() {
        return "BINARY(16)";
    }
}
