/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.metrics.ContextFactory;
import org.apache.hadoop.metrics.MetricsContext;
import org.apache.hadoop.metrics.spi.NullContext;
import org.junit.jupiter.api.Test;

public class ContextFactoryTest {
    @Test
    void getFactoryCreatesDefaultNullContext() throws Exception {
        String contextName = "contextFactoryTest-" + System.nanoTime();

        ContextFactory factory = ContextFactory.getFactory();
        MetricsContext context = factory.getContext(contextName);

        try {
            assertThat(context).isInstanceOf(NullContext.class);
            assertThat(context.getContextName()).isEqualTo(contextName);
            assertThat(factory.getAllContexts()).contains(context);
        } finally {
            context.close();
        }
    }
}
