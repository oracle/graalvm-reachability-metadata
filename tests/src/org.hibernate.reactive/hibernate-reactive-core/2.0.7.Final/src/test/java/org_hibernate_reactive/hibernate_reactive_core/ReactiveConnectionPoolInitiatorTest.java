/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.hibernate.engine.jdbc.spi.SqlExceptionHelper;
import org.hibernate.reactive.pool.ReactiveConnection;
import org.hibernate.reactive.pool.ReactiveConnectionPool;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.service.ServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveConnectionPoolInitiatorTest implements ReactiveConnectionPool {

    @Test
    void instantiatesConfiguredReactiveConnectionPoolClass() {
        Map<String, Object> configurationValues = new HashMap<>();
        configurationValues.put(Settings.SQL_CLIENT_POOL, ReactiveConnectionPoolInitiatorTest.class);

        ServiceRegistry serviceRegistry = new ReactiveServiceRegistryBuilder()
                .applySettings(configurationValues)
                .build();
        try {
            ReactiveConnectionPool pool = serviceRegistry.getService(ReactiveConnectionPool.class);

            assertThat(pool).isInstanceOf(ReactiveConnectionPoolInitiatorTest.class);
            assertThat(pool.getCloseFuture().toCompletableFuture().isDone()).isTrue();
        } finally {
            ReactiveServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    @Override
    public CompletionStage<ReactiveConnection> getConnection() {
        return unsupportedConnectionRequest();
    }

    @Override
    public CompletionStage<ReactiveConnection> getConnection(SqlExceptionHelper sqlExceptionHelper) {
        return unsupportedConnectionRequest();
    }

    @Override
    public CompletionStage<ReactiveConnection> getConnection(String tenantId) {
        return unsupportedConnectionRequest();
    }

    @Override
    public CompletionStage<ReactiveConnection> getConnection(String tenantId, SqlExceptionHelper sqlExceptionHelper) {
        return unsupportedConnectionRequest();
    }

    @Override
    public CompletionStage<Void> getCloseFuture() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletionStage<ReactiveConnection> unsupportedConnectionRequest() {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("connection acquisition is not used in this test")
        );
    }
}
