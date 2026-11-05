/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous15Test {
    @Test
    void resolvesAndInvokesMethodByName() throws Exception {
        ReflectionFixture instance = new ReflectionFixture();
        String result = SecureReflections.invoke(instance, "greet", "Grace");
        assertThat(result).isEqualTo("Hello Grace");
        assertThat(instance.label).isEqualTo("Hello Grace");
    }
}
