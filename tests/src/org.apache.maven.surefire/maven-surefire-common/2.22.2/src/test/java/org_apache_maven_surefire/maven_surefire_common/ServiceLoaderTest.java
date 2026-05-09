/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.providerapi.ServiceLoader;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceLoaderTest {
    @Test
    void loadsProviderClassNamesFromContextClassLoaderResources() {
        ClassLoader classLoader = ServiceLoaderTest.class.getClassLoader();

        Set<ServiceLoaderTest> services = new ServiceLoader().load(ServiceLoaderTest.class, classLoader);

        assertThat(services).singleElement().isInstanceOf(ServiceLoaderTest.class);
    }
}
