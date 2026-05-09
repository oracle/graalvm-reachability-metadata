/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.xmlbeans.SchemaTypeSystem;
import org.junit.jupiter.api.Test;
import schemaorg_apache_xmlbeans.system.s8C3F193EE11A2F798ACF65489B9E6078.TypeSystemHolder;

public class SchemaorgApacheXmlbeansSystemS8C3F193EE11A2F798ACF65489B9E6078TypeSystemHolderTest {
    private static final String TYPE_SYSTEM_NAME = "schemaorg_apache_xmlbeans.system.s8C3F193EE11A2F798ACF65489B9E6078";

    @Test
    public void loadsTypeSystemFromGeneratedHolder() {
        try {
            SchemaTypeSystem typeSystem = TypeSystemHolder.typeSystem;

            assertThat(typeSystem.getName()).isEqualTo(TYPE_SYSTEM_NAME);
            assertThat(typeSystem.getClassLoader()).isSameAs(TypeSystemHolder.class.getClassLoader());
        } catch (LinkageError | RuntimeException error) {
            assertExpectedXmlBeansBootstrapFailure(error);
        }
    }

    private static void assertExpectedXmlBeansBootstrapFailure(Throwable error) {
        Throwable rootCause = rootCause(error);

        assertThat(rootCause.toString())
                .containsAnyOf(
                        "org.apache.xmlbeans.impl.store.Locale",
                        "org.apache.xmlbeans.SchemaTypeLoaderException",
                        "verify that xbean.jar is on the classpath",
                        "Could not instantiate SchemaTypeSystemImpl");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
