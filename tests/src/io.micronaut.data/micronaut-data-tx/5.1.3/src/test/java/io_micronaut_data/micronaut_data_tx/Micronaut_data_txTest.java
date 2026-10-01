/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_data.micronaut_data_tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.data.connection.ConnectionDefinition;
import io.micronaut.transaction.TransactionCallback;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionDefinition.Isolation;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionOperationsRegistry;
import io.micronaut.transaction.support.DefaultTransactionDefinition;
import org.junit.jupiter.api.Test;

public class Micronaut_data_txTest {

    @Test
    void createsDefinitionsForCommonTransactionModes() {
        TransactionDefinition write = TransactionDefinition.DEFAULT;
        TransactionDefinition read = TransactionDefinition.READ_ONLY;

        assertThat(write.getPropagationBehavior()).isEqualTo(Propagation.REQUIRED);
        assertThat(write.getIsolationLevel()).isEmpty();
        assertThat(write.isReadOnly()).isEmpty();
        assertThat(read.getPropagationBehavior()).isEqualTo(Propagation.REQUIRED);
        assertThat(read.isReadOnly()).contains(true);
        assertThat(read.getName()).isEqualTo("READ_ONLY");

        TransactionDefinition requiresNew = TransactionDefinition.of(Propagation.REQUIRES_NEW);
        assertThat(requiresNew.getPropagationBehavior()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(requiresNew.getConnectionDefinition().getPropagationBehavior())
                .isEqualTo(ConnectionDefinition.Propagation.REQUIRES_NEW);
    }

    @Test
    void createsNamedDefinitionForTransactionTracing() {
        TransactionDefinition definition = TransactionDefinition.named("catalog-read");

        assertThat(definition.getName()).isEqualTo("catalog-read");
        assertThat(definition.getPropagationBehavior()).isEqualTo(Propagation.REQUIRED);
        assertThat(definition.getConnectionDefinition().getPropagationBehavior())
                .isEqualTo(ConnectionDefinition.Propagation.REQUIRED);
    }

    @Test
    void preservesConfiguredDefinitionOptionsAndProperties() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(Propagation.SUPPORTS);
        definition.setIsolationLevel(Isolation.SERIALIZABLE);
        definition.setTimeout(Duration.ofSeconds(30));
        definition.setReadOnly(true);
        definition.setName("catalog-read");
        definition.setRollbackOn(List.of(IllegalStateException.class));
        definition.setDontRollbackOn(List.of(IllegalArgumentException.class));
        definition.setProperties(Map.of("tenant", "catalog"));
        definition.putProperty("trace", "enabled");

        assertThat(definition.getPropagationBehavior()).isEqualTo(Propagation.SUPPORTS);
        assertThat(definition.getIsolationLevel()).contains(Isolation.SERIALIZABLE);
        assertThat(definition.getTimeout()).contains(Duration.ofSeconds(30));
        assertThat(definition.isReadOnly()).contains(true);
        assertThat(definition.getName()).isEqualTo("catalog-read");
        assertThat(definition.getProperties())
                .containsEntry("tenant", "catalog")
                .containsEntry("trace", "enabled");
        assertThat(definition.rollbackOn(new IllegalStateException())).isTrue();
        assertThat(definition.rollbackOn(new IllegalArgumentException())).isFalse();
        assertThat(definition.rollbackOn(new RuntimeException())).isFalse();
    }

    @Test
    void convertsSupportedDefinitionToDefaultConnectionPropagation() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(Propagation.SUPPORTS);
        definition.setName("inventory-read");

        ConnectionDefinition connectionDefinition = definition.getConnectionDefinition();

        assertThat(connectionDefinition.getPropagationBehavior())
                .isEqualTo(ConnectionDefinition.Propagation.REQUIRED);
        assertThat(connectionDefinition.getName()).isEqualTo("inventory-read");
    }

    @Test
    void copiesDefinitionsWithoutSharingMutableProperties() {
        DefaultTransactionDefinition original = new DefaultTransactionDefinition();
        original.setName("original");
        original.putProperty("mode", "read");

        DefaultTransactionDefinition copy = new DefaultTransactionDefinition(original);
        copy.putProperty("mode", "write");
        copy.putProperty("new-property", 42);

        assertThat(copy.getName()).isEqualTo("original");
        assertThat(copy.getProperties()).containsEntry("mode", "write")
                .containsEntry("new-property", 42);
        assertThat(original.getProperties()).containsEntry("mode", "read")
                .doesNotContainKey("new-property");
        assertThat(copy).isNotEqualTo(original);
    }

    @Test
    void discoversTransactionOperationsRegistryThroughMicronautContext() {
        try (ApplicationContext context = ApplicationContext.run()) {
            TransactionOperationsRegistry registry =
                    context.getBean(TransactionOperationsRegistry.class);

            assertThatThrownBy(
                            () -> registry.provideSynchronous(TransactionOperations.class, null))
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("No backing TransactionOperations configured");
        }
    }

    @Test
    void invokesTransactionCallbackAndPropagatesCheckedFailures() {
        TransactionCallback<String, String> callback = status -> "completed";
        assertThat(callback.apply(null)).isEqualTo("completed");

        TransactionCallback<String, String> failingCallback = status -> {
            throw new Exception("callback failed");
        };

        assertThatThrownBy(() -> failingCallback.apply(null))
                .isInstanceOf(Exception.class)
                .hasMessage("callback failed");
    }
}
