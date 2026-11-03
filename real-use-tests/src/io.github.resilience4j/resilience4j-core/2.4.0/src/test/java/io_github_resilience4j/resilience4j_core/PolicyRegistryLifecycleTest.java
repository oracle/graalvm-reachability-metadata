/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.registry.AbstractRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manages named application policies with Resilience4j's registry lifecycle subsystem.
 *
 * A checkout service reuses a policy by name, applies a named configuration to a new policy, and
 * observes additions, replacements, and removals so its administration view stays current.
 */
class PolicyRegistryLifecycleTest {

    @Test
    void managesNamedPoliciesAndPublishesLifecycleEvents() {
        PolicyConfiguration defaultConfiguration = new PolicyConfiguration(Duration.ofSeconds(2));
        PolicyConfiguration checkoutConfiguration = new PolicyConfiguration(Duration.ofSeconds(5));
        List<String> lifecycleEvents = new ArrayList<>();
        RegistryEventConsumer<Policy> eventConsumer = new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<Policy> event) {
                lifecycleEvents.add("added:" + event.getAddedEntry().name());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<Policy> event) {
                lifecycleEvents.add("removed:" + event.getRemovedEntry().name());
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<Policy> event) {
                lifecycleEvents.add("replaced:" + event.getOldEntry().name()
                        + "->" + event.getNewEntry().name());
            }
        };
        PolicyRegistry registry = new PolicyRegistry(defaultConfiguration, eventConsumer);
        registry.addConfiguration("checkout", checkoutConfiguration);

        Policy initialPolicy = registry.getOrCreate("checkout", () -> new Policy(
                "checkout", registry.getConfiguration("checkout").orElse(registry.getDefaultConfig())));
        Policy reusedPolicy = registry.getOrCreate("checkout", () -> new Policy(
                "unexpected", registry.getDefaultConfig()));
        Policy replacementPolicy = new Policy("checkout", new PolicyConfiguration(Duration.ofSeconds(8)));

        assertThat(reusedPolicy).isSameAs(initialPolicy);
        assertThat(initialPolicy.configuration()).isEqualTo(checkoutConfiguration);
        assertThat(registry.find("checkout")).contains(initialPolicy);
        assertThat(registry.getTags()).containsEntry("service", "checkout");

        assertThat(registry.replace("checkout", replacementPolicy)).contains(initialPolicy);
        assertThat(registry.remove("checkout")).contains(replacementPolicy);
        assertThat(registry.find("checkout")).isEmpty();
        assertThat(lifecycleEvents).containsExactly(
                "added:checkout", "replaced:checkout->checkout", "removed:checkout");
    }

    private static final class PolicyRegistry extends AbstractRegistry<Policy, PolicyConfiguration> {

        private PolicyRegistry(
                PolicyConfiguration defaultConfiguration,
                RegistryEventConsumer<Policy> eventConsumer) {
            super(defaultConfiguration, eventConsumer, Map.of("service", "checkout"));
        }

        private Policy getOrCreate(String name, Supplier<Policy> supplier) {
            return computeIfAbsent(name, supplier);
        }
    }

    private record Policy(String name, PolicyConfiguration configuration) {
    }

    private record PolicyConfiguration(Duration timeout) {
    }
}
