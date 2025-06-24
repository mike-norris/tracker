package com.openrangelabs.tracer.util;

import com.openrangelabs.tracer.config.DatabaseType;
import com.openrangelabs.tracer.model.converters.MongoUuidConverter;
import com.openrangelabs.tracer.model.converters.MysqlUuidConverter;
import com.openrangelabs.tracer.model.converters.PostgresUuidConverter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Factory for creating appropriate UUID converters based on database type.
 * Provides thread-safe singleton instances for optimal performance.
 */
@Component
public class UuidConverterFactory {

    private final Map<DatabaseType, UuidConverter> converterCache = new ConcurrentHashMap<>();

    /**
     * Get the appropriate UUID converter for the specified database type
     */
    public UuidConverter getConverter(DatabaseType databaseType) {
        return converterCache.computeIfAbsent(databaseType, this::createConverter);
    }

    /**
     * Create a new UUID converter instance for the specified database type
     */
    private UuidConverter createConverter(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> new PostgresUuidConverter();
            case MYSQL, MARIADB -> new MysqlUuidConverter();
            case MONGODB -> new MongoUuidConverter();
        };
    }

    /**
     * Get column definition for UUID fields in the specified database type
     */
    public String getUuidColumnDefinition(DatabaseType databaseType) {
        return getConverter(databaseType).getColumnDefinition();
    }

    /**
     * Check if the database type supports native UUID storage
     */
    public boolean supportsNativeUuid(DatabaseType databaseType) {
        return databaseType == DatabaseType.POSTGRESQL || databaseType == DatabaseType.MONGODB;
    }

    /**
     * Check if the database type requires binary conversion for optimal storage
     */
    public boolean requiresBinaryConversion(DatabaseType databaseType) {
        return databaseType == DatabaseType.MYSQL || databaseType == DatabaseType.MARIADB;
    }

    /**
     * Get storage efficiency information for the database type
     */
    public UuidStorageInfo getStorageInfo(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> new UuidStorageInfo(
                    "UUID", 16, true, "Native PostgreSQL UUID type with optimal indexing"
            );
            case MYSQL, MARIADB -> new UuidStorageInfo(
                    "BINARY(16)", 16, false, "Binary storage for optimal space efficiency"
            );
            case MONGODB -> new UuidStorageInfo(
                    "BinData", 16, true, "Native MongoDB UUID as BinData subtype 4"
            );
        };
    }

    /**
     * Information about UUID storage characteristics for a database type
     */
    public record UuidStorageInfo(
            String storageType,
            int storageBytes,
            boolean nativeSupport,
            String description
    ) {}
}