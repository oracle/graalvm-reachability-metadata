/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.plugin.surefire.CommonReflector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommonReflectorTest {
    @Test
    void loadsSurefireApiTypesFromSuppliedClassLoader() {
        ClassLoader classLoader = CommonReflectorTest.class.getClassLoader();

        CommonReflector reflector = new CommonReflector(classLoader);

        assertThat(reflector).isNotNull();
    }
}
