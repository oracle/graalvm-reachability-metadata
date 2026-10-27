/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.ClassLoading;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleClassLoadingTest {
    private static final String TEST_RESOURCE = "org_hibernate_models/hibernate_models/simple-class-loading.txt";

    @Test
    public void findsExistingClassByName() {
        final ClassLoading classLoading = SimpleClassLoading.SIMPLE_CLASS_LOADING;

        final Class<?> foundClass = classLoading.findClassForName(SimpleClassLoadingTest.class.getName());

        assertThat(foundClass).isEqualTo(SimpleClassLoadingTest.class);
    }

    @Test
    public void locatesClassPathResourceByName() throws IOException {
        final ClassLoading classLoading = SimpleClassLoading.SIMPLE_CLASS_LOADING;

        final URL resourceUrl = classLoading.locateResource(TEST_RESOURCE);

        assertThat(resourceUrl).isNotNull();
        try (InputStream inputStream = resourceUrl.openStream()) {
            final String resourceContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(resourceContent).contains("simple class loading resource");
        }
    }
}
