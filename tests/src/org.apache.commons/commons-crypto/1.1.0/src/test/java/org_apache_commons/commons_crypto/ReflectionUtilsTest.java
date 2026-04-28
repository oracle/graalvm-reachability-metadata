/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import org.apache.commons.crypto.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void loadsClassesAndCreatesInstances() throws ClassNotFoundException {
        assertThat(ReflectionUtils.getClassByName(String.class.getName())).isSameAs(String.class);
        assertThat(ReflectionUtils.newInstance(StringBuilder.class).toString()).isEmpty();
        assertThat(ReflectionUtils.newInstance(String.class, "commons-crypto")).isEqualTo("commons-crypto");
    }
}
