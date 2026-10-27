/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.internal.ReflectionUtilsAccess;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void loadsClassWithDefaultImplementationClassLoader() {
        final String className = ReflectionUtilsAccess.loadClassName(ReflectionUtilsFixture.class.getName());

        assertThat(className).isEqualTo(ReflectionUtilsFixture.class.getName());
    }

    @Test
    void findsPublicConstructor() {
        final String declaringClassName = ReflectionUtilsAccess.constructorDeclaringClassName(
                ReflectionUtilsFixture.class.getName(), String.class);

        assertThat(declaringClassName).isEqualTo(ReflectionUtilsFixture.class.getName());
    }

    @Test
    void findsPublicStaticMethod() {
        final String returnTypeName = ReflectionUtilsAccess.staticMethodReturnTypeName(
                ReflectionUtilsFixture.class.getName(), "describe", Integer.class);

        assertThat(returnTypeName).isEqualTo(String.class.getName());
    }

}
