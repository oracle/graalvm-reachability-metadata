/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import org.apache.maven.shared.utils.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassMapInnerMethodInfoTest {
    @Test
    void extractsPropertyFromNonPublicInterfaceImplementation() throws Exception {
        DisplayName component = new HiddenComponent("worker");

        assertEquals("worker", ReflectionValueExtractor.evaluate("name", component, false));
    }

    @Test
    void extractsOverriddenPropertyThroughPublicSuperclass() throws Exception {
        Component component = new HiddenSpecializedComponent();

        assertEquals("specialized", ReflectionValueExtractor.evaluate("name", component, false));
    }

    public interface DisplayName {
        String getName();
    }

    static final class HiddenComponent implements DisplayName {
        private final String name;

        HiddenComponent(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public static class Component {
        public String getName() {
            return "base";
        }
    }

    static final class HiddenSpecializedComponent extends Component {
        @Override
        public String getName() {
            return "specialized";
        }
    }
}
