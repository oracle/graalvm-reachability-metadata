/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesNestedBeanPropertyPath() throws Exception {
        ReflectionValueExtractorRoot root = new ReflectionValueExtractorRoot(
                new ReflectionValueExtractorChild("configured-value"));

        Object value = ReflectionValueExtractor.evaluate("project.child.displayName", root);

        assertThat(value).isEqualTo("configured-value");
    }

    public static class ReflectionValueExtractorRoot {
        private final ReflectionValueExtractorChild child;

        public ReflectionValueExtractorRoot(ReflectionValueExtractorChild child) {
            this.child = child;
        }

        public ReflectionValueExtractorChild getChild() {
            return child;
        }
    }

    public static class ReflectionValueExtractorChild {
        private final String displayName;

        public ReflectionValueExtractorChild(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
