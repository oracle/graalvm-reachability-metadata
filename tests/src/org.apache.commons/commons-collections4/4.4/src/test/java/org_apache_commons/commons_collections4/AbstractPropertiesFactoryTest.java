/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.properties.PropertiesFactory;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertiesFactoryTest {

    @Test
    void loadsPropertiesFromClasspathResourceUsingProvidedClassLoader() throws Exception {
        Properties properties = PropertiesFactory.INSTANCE.load(
                AbstractPropertiesFactoryTest.class.getClassLoader(),
                "org_apache_commons/commons_collections4/abstract-properties-factory-test.properties"
        );

        assertThat(properties)
                .isNotNull()
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");
    }
}
