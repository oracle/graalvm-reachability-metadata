/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.ViolationStore;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class ViolationStoreFactoryTest {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Test
    void freezingRuleInstantiatesConfiguredViolationStore() {
        INITIALIZED.set(false);
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setProperty("freeze.store", RecordingViolationStore.class.getName());
            JavaClasses importedClasses = TestClassImporter.importClasses(AnnotatedFixture.class);
            FreezingArchRule rule = FreezingArchRule.freeze(classes().should().bePublic());

            assertThat(rule.evaluate(importedClasses).hasViolation()).isFalse();
            assertThat(INITIALIZED).isTrue();
        });
    }

    public static final class RecordingViolationStore implements ViolationStore {
        public RecordingViolationStore() { }

        @Override
        public void initialize(Properties properties) {
            INITIALIZED.set(true);
        }

        @Override
        public boolean contains(ArchRule rule) {
            return false;
        }

        @Override
        public void save(ArchRule rule, List<String> violations) { }

        @Override
        public List<String> getViolations(ArchRule rule) {
            return List.of();
        }
    }
}
