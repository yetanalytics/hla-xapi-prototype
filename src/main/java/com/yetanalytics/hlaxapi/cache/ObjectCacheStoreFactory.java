package com.yetanalytics.hlaxapi.cache;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

final class ObjectCacheStoreFactory {

    private ObjectCacheStoreFactory() {
    }

    static ObjectCacheStore open(ObjectCacheConnectionSettings settings, FomCatalog catalog) {
        Connection connection = null;
        try {
            Properties properties = new Properties();
            if (settings.username() != null) {
                properties.setProperty("user", settings.username());
                properties.setProperty("password", settings.password());
            }
            connection = DriverManager.getConnection(settings.jdbcUrl(), properties);
            ObjectCacheQueries queries = switch (settings.backend()) {
                case SQLITE -> new SqliteObjectCacheQueries();
                case POSTGRESQL -> new PostgresqlObjectCacheQueries(settings.schema());
            };
            return new JdbcObjectCacheStore(connection, queries, catalog);
        } catch (SQLException | RuntimeException e) {
            closeAfterFailure(connection, e);
            throw new IllegalStateException(
                    "Could not initialize " + settings.backend() + " HLA object cache", e);
        }
    }

    private static void closeAfterFailure(Connection connection, Exception failure) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeError) {
            failure.addSuppressed(closeError);
        }
    }
}
