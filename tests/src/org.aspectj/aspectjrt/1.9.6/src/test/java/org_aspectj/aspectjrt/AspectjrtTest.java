/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;

public class AspectjrtTest {
    @Test
    void exposesAspectjRuntimeApi() {
        assertThat(JoinPoint.class.getName()).isEqualTo("org.aspectj.lang.JoinPoint");
    }
}
