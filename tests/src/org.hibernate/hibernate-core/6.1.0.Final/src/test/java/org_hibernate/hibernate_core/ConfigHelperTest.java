/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import java.io.IOException;
import java.io.InputStream;

import org.hibernate.HibernateException;
import org.hibernate.internal.util.ConfigHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigHelperTest {

    @Test
    public void findsNoUrlForAnUnknownResource() {
        assertThat(ConfigHelper.findAsResource("missing-hibernate-config-helper-resource"))
                .isNull();
    }

    @Test
    public void opensClassRelativeResourceWhenNoContextClassLoaderIsAvailable() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try (InputStream stream = ConfigHelper.getResourceAsStream("/hibernate.properties")) {
            assertThat(stream.read()).isNotEqualTo(-1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void reportsMissingResourceWhenNoContextClassLoaderIsAvailable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            assertThatThrownBy(() -> ConfigHelper.getResourceAsStream(
                    "missing-hibernate-config-helper-resource"))
                    .isInstanceOf(HibernateException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void opensUserResourceWithLeadingSlash() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        try (InputStream stream = ConfigHelper.getUserResourceAsStream("/hibernate.properties")) {
            assertThat(stream.read()).isNotEqualTo(-1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void opensUserResourceFromHibernateClassLoader() throws IOException {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            try (InputStream stream = ConfigHelper.getUserResourceAsStream("hibernate.properties")) {
                assertThat(stream.read()).isNotEqualTo(-1);
            }
            try (InputStream stream = ConfigHelper.getUserResourceAsStream("/hibernate.properties")) {
                assertThat(stream.read()).isNotEqualTo(-1);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void reportsMissingUserResource() {
        assertThatThrownBy(() -> ConfigHelper.getUserResourceAsStream(
                "/missing-hibernate-config-helper-resource"))
                .isInstanceOf(HibernateException.class);
    }
}
