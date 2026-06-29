/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import org.junit.jupiter.api.Test;

public class LineNumbersTest {
    @Test
    void formatsMissingConstructorDependencyWithSourceLineNumbers() {
        assertThatThrownBy(() -> Guice.createInjector().getInstance(ConstructorTarget.class))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("No implementation for " + MissingService.class.getName())
                .hasMessageContaining("for parameter 0 at")
                .hasMessageContaining("LineNumbersTest.java");
    }

    public static class ConstructorTarget {
        @Inject
        public ConstructorTarget(MissingService service) {
        }
    }

    public interface MissingService {
    }
}
