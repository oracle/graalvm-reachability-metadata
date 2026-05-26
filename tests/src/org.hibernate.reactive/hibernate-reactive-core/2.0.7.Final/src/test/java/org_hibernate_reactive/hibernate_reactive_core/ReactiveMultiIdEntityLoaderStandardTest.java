/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.SessionCreationOptions;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.loader.ast.spi.MultiIdLoadOptions;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.reactive.common.spi.Implementor;
import org.hibernate.reactive.persister.entity.impl.ReactiveEntityPersister;
import org.hibernate.reactive.pool.ReactiveConnectionPool;
import org.hibernate.reactive.session.impl.ReactiveSessionImpl;
import org.hibernate.reactive.stage.Stage.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org_hibernate_reactive.hibernate_reactive_core.entity.BatchAuthor;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReactiveMultiIdEntityLoaderStandardTest {

    private static final Logger logger = LoggerFactory.getLogger("ReactiveMultiIdEntityLoaderStandardTest");

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String DATABASE = "test";

    private static final String JDBC_URL = "jdbc:mysql://localhost:3307/" + DATABASE;

    private static Process process;

    private EntityManagerFactory entityManagerFactory;

    private SessionFactory factory;

    @BeforeAll
    public void init() throws IOException {
        logger.info("Starting MySQL ...");
        process = new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "-p",
                "3307:3306",
                "-e",
                "MYSQL_DATABASE=" + DATABASE,
                "-e",
                "MYSQL_USER=" + USERNAME,
                "-e",
                "MYSQL_PASSWORD=" + PASSWORD,
                "container-registry.oracle.com/mysql/community-server:9.6.0")
                .inheritIO()
                .start();

        waitUntil(() -> {
            openConnection().close();
            return true;
        }, 45, 1);

        logger.info("MySQL started");

        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", JDBC_URL);
        properties.put("jakarta.persistence.jdbc.user", USERNAME);
        properties.put("jakarta.persistence.jdbc.password", PASSWORD);
        entityManagerFactory = Persistence.createEntityManagerFactory("mysql-standard-multiload", properties);
        factory = entityManagerFactory.unwrap(SessionFactory.class);
    }

    @AfterAll
    public void close() {
        if (factory != null) {
            factory.close();
        }
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        if (process != null && process.isAlive()) {
            logger.info("Shutting down MySQL");
            process.destroy();
        }
    }

    @Test
    void unorderedMultiLoadUsesManagedEntityAndLoadsRemainingIds() {
        BatchAuthor alreadyManaged = new BatchAuthor("already managed");
        BatchAuthor loadedFromDatabase = new BatchAuthor("loaded from database");

        factory.withTransaction((session, transaction) -> session.persist(alreadyManaged, loadedFromDatabase))
                .toCompletableFuture()
                .join();

        List<?> authors = awaitInReactiveContext(() -> openReactiveSession()
                .thenCompose(session -> session.reactiveFind(BatchAuthor.class, alreadyManaged.getId(), null, null)
                        .thenCompose(cachedAuthor -> {
                            assertThat(cachedAuthor.getName()).isEqualTo(alreadyManaged.getName());
                            return reactivePersister().reactiveMultiLoad(
                                    new Long[] { alreadyManaged.getId(), loadedFromDatabase.getId() },
                                    session,
                                    new UnorderedSessionCheckingOptions());
                        })
                        .handle((loadedAuthors, failure) -> session.reactiveClose()
                                .handle((ignored, closeFailure) -> {
                                    if (failure != null) {
                                        throw new CompletionException(failure);
                                    }
                                    if (closeFailure != null) {
                                        throw new CompletionException(closeFailure);
                                    }
                                    return loadedAuthors;
                                }))
                        .thenCompose(closedSession -> closedSession)));

        assertThat(authors)
                .hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder(alreadyManaged.getName(), loadedFromDatabase.getName());
    }

    private ReactiveEntityPersister reactivePersister() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EntityPersister entityPersister = sessionFactory.getMetamodel().entityPersister(BatchAuthor.class);
        return (ReactiveEntityPersister) entityPersister;
    }

    private CompletionStage<ReactiveSessionImpl> openReactiveSession() {
        SessionFactoryImpl sessionFactory = entityManagerFactory.unwrap(SessionFactoryImpl.class);
        ReactiveConnectionPool connectionPool = sessionFactory.getServiceRegistry()
                .getService(ReactiveConnectionPool.class);
        SessionCreationOptions options = new SessionFactoryImpl.SessionBuilderImpl(sessionFactory);
        return connectionPool.getConnection()
                .thenApply(connection -> new ReactiveSessionImpl(sessionFactory, options, connection));
    }

    private <T> T awaitInReactiveContext(Supplier<CompletionStage<T>> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ((Implementor) factory).getContext().execute(() -> {
            try {
                work.get().whenComplete((value, failure) -> {
                    if (failure != null) {
                        future.completeExceptionally(failure);
                    }
                    else {
                        future.complete(value);
                    }
                });
            }
            catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.join();
    }

    private void waitUntil(Callable<Boolean> conditionEvaluator, int timeoutSeconds, int sleepTimeSeconds) {
        Exception lastException = null;

        long end = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(sleepTimeSeconds * 1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            }
            catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null
                ? new IllegalStateException(errorMessage)
                : new IllegalStateException(errorMessage, lastException);
    }

    private static Connection openConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(JDBC_URL, props);
    }

    private static class UnorderedSessionCheckingOptions implements MultiIdLoadOptions {

        @Override
        public boolean isSessionCheckingEnabled() {
            return true;
        }

        @Override
        public boolean isSecondLevelCacheCheckingEnabled() {
            return false;
        }

        @Override
        public boolean isReturnOfDeletedEntitiesEnabled() {
            return false;
        }

        @Override
        public boolean isOrderReturnEnabled() {
            return false;
        }

        @Override
        public LockOptions getLockOptions() {
            return new LockOptions(LockMode.NONE);
        }

        @Override
        public Integer getBatchSize() {
            return 2;
        }
    }
}
