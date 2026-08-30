/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.net.URL;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ClassLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilsTest {

    @Test
    void getsLocationForClassLoadedByApplicationClassLoader() {
        LocationSubject subject = new LocationSubject();

        Optional<URL> location = ClassLoaderUtils.getLocation(subject);

        assertThat(location).isNotNull();
        location.ifPresent(url -> assertThat(url.toExternalForm()).isNotBlank());
    }

    public static class LocationSubject {
    }
}
