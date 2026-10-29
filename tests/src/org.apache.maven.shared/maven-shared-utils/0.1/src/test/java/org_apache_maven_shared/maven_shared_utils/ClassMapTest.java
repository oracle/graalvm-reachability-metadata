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

public class ClassMapTest {
    @Test
    void extractsPublicBeanPropertyRepeatedly() throws Exception {
        NamedComponent component = new NamedComponent();

        assertEquals("component", ReflectionValueExtractor.evaluate("name", component, false));
        assertEquals("component", ReflectionValueExtractor.evaluate("name", component, false));
    }

    @Test
    void extractsPropertyInheritedFromNonPublicImplementation() throws Exception {
        PublicComponent component = new PublicComponent();

        assertEquals("configured", ReflectionValueExtractor.evaluate("name", component, false));
    }

    public static class NamedComponent {
        public String getName() {
            return "component";
        }
    }

    static class ConfiguredComponent extends NamedComponent {
        @Override
        public final String getName() {
            return "configured";
        }
    }

    public static class PublicComponent extends ConfiguredComponent {
    }
}
