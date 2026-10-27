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
import org.springframework.batch.infrastructure.item.adapter.ItemProcessorAdapter;

public class AbstractMethodInvokingDelegatorTest {
    @Test
    void validatesAndInvokesConfiguredTargetMethod() throws Exception {
        ExecutionContext context = new ExecutionContext();
        context.putString("ready", "yes");

        ItemProcessorAdapter<String, Boolean> processor = new ItemProcessorAdapter<>();
        processor.setTargetObject(context);
        processor.setTargetMethod("containsKey");
        processor.afterPropertiesSet();

        assertThat(processor.process("ready")).isTrue();
        assertThat(processor.process("missing")).isFalse();
    }
}
