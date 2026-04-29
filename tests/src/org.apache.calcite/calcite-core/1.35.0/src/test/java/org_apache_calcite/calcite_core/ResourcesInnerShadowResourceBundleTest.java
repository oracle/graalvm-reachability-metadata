/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerShadowResourceBundleTest extends Resources.ShadowResourceBundle {
    public ResourcesInnerShadowResourceBundleTest() throws IOException {
        super();
    }

    @Test
    public void loadsPropertiesThroughShadowResourceBundle() {
        assertThat(getString("message")).isEqualTo("shadow bundle loaded");
        assertThat(getString("format")).isEqualTo("Hello {0}");
        assertThat(Collections.list(getKeys())).contains("message", "format");
    }

    @Test
    public void fallsBackToSystemClassLoaderForBootstrapClasses() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                Resources.ShadowResourceBundle.class, MethodHandles.lookup());
        MethodHandle openPropertiesFile = lookup.findStatic(
                Resources.ShadowResourceBundle.class,
                "openPropertiesFile",
                MethodType.methodType(InputStream.class, Class.class));

        try (InputStream stream = (InputStream) openPropertiesFile.invoke(String.class)) {
            assertThat(stream).isNull();
        }
    }
}
