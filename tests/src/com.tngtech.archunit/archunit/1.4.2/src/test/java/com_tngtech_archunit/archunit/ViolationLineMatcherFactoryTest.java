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
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.ViolationLineMatcher;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ViolationLineMatcherFactoryTest {
    private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    @Test
    void freezingRuleInstantiatesConfiguredLineMatcher() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setProperty(
                    "freeze.lineMatcher", ExactLineMatcher.class.getName());

            FreezingArchRule rule = FreezingArchRule.freeze(classes().should().bePublic());

            assertThat(rule.getDescription()).contains("classes should be public");
            assertThat(CONSTRUCTIONS).hasValue(1);
        });
    }

    public static final class ExactLineMatcher implements ViolationLineMatcher {
        public ExactLineMatcher() {
            CONSTRUCTIONS.incrementAndGet();
        }

        @Override
        public boolean matches(String first, String second) {
            return first.equals(second);
        }
    }
}
