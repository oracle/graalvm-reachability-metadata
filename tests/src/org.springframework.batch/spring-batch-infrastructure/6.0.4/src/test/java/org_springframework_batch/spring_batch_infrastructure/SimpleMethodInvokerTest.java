/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_batch.spring_batch_infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.support.SimpleMethodInvoker;

public class SimpleMethodInvokerTest {
    @Test
    void invokesNamedMethodWithArguments() {
        ExecutionContext context = new ExecutionContext();
        context.putString("status", "complete");
        SimpleMethodInvoker invoker =
                new SimpleMethodInvoker(context, "getString", String.class);

        assertThat(invoker.invokeMethod("status")).isEqualTo("complete");
    }
}
