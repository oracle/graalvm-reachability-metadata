/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.ConfigHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigHelperTest {

    @Test
    public void loadsApplicationResourcesThroughSupportedLookupForms() throws Exception {
        URL contextResource = ConfigHelper.findAsResource("hibernate-coverage.cfg.xml");
        try (InputStream contextResourceStream = ConfigHelper.getResourceAsStream(
                "hibernate-coverage.cfg.xml"
        ); InputStream contextUserResource = ConfigHelper.getUserResourceAsStream(
                "/hibernate-coverage.cfg.xml"
        )) {
            assertThat(contextResourceStream.read()).isNotNegative();
            assertThat(contextUserResource.read()).isNotNegative();
        }

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        URL libraryResource;
        URL missingResource;
        try {
            Thread.currentThread().setContextClassLoader(null);
            libraryResource = ConfigHelper.findAsResource("hibernate-coverage.cfg.xml");
            missingResource = ConfigHelper.findAsResource("missing-hibernate-resource.xml");
            try (InputStream resource = ConfigHelper.getResourceAsStream(
                    "hibernate-coverage.cfg.xml"
            ); InputStream userResource = ConfigHelper.getUserResourceAsStream(
                    "/hibernate-coverage.cfg.xml"
            )) {
                assertThat(resource.read()).isNotNegative();
                assertThat(userResource.read()).isNotNegative();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }

        assertThat(contextResource).isNotNull();
        assertThat(libraryResource).isNotNull();
        assertThat(missingResource).isNull();
    }
}
