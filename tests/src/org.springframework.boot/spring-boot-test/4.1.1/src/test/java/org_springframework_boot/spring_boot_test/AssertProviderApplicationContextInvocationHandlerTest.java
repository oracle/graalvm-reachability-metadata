/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class AssertProviderApplicationContextInvocationHandlerTest {
    @Test
    void assertableContextDelegatesApplicationContextMethods() {
        new ApplicationContextRunner()
                .withBean("answer", Integer.class, () -> 42)
                .run(
                        (context) -> {
                            assertThat(context.containsBean("answer")).isTrue();
                            assertThat(context.getBean("answer", Integer.class)).isEqualTo(42);
                        });
    }
}
