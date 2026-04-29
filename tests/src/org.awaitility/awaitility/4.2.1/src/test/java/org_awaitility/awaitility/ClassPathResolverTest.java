/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_awaitility.awaitility;

import org.awaitility.classpath.ClassPathResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResolverTest {

    @Test
    void reportsAwaitilityClassAsPresentOnClasspath() {
        boolean present = ClassPathResolver.existInCP(ClassPathResolver.class.getName());

        assertThat(present).isTrue();
    }
}
