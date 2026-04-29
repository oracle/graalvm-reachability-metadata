/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ResourceBundle;
import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesInnerShadowResourceBundleTest {
    @Test
    void loadsPropertiesForBundleClassAndItsParentClass() {
        ResourceBundle bundle = ResourceBundle.getBundle(LocalizedMessages.class.getName());

        assertThat(bundle).isInstanceOf(LocalizedMessages.class);
        assertThat(bundle.getString("greeting")).isEqualTo("Hello from child bundle");
        assertThat(bundle.getString("fallback")).isEqualTo("Parent bundle fallback");
    }

    @Test
    void usesSystemResourceLookupForBootstrapLoadedClasses() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Resources.ShadowResourceBundle.class,
                MethodHandles.lookup());
        MethodHandle openPropertiesFile = lookup.findStatic(
                Resources.ShadowResourceBundle.class,
                "openPropertiesFile",
                MethodType.methodType(InputStream.class, Class.class));

        try (InputStream stream = (InputStream) openPropertiesFile.invokeExact(Object.class)) {
            assertThat(stream).isNull();
        }
    }

    public static class ParentMessages extends Resources.ShadowResourceBundle {
        public ParentMessages() throws IOException {
        }
    }

    public static class LocalizedMessages extends ParentMessages {
        public LocalizedMessages() throws IOException {
        }
    }
}
