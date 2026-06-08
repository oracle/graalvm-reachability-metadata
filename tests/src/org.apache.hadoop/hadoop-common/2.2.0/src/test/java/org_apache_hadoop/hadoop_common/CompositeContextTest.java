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
import org.apache.hadoop.metrics.MetricsRecord;
import org.apache.hadoop.metrics.spi.CompositeContext;
import org.apache.hadoop.metrics.spi.NullContext;
import org.junit.jupiter.api.Test;

public class CompositeContextTest {
    @Test
    void configuredCompositeContextCreatesMetricsRecordProxy() throws Exception {
        String contextName = "compositeContextTest-" + System.nanoTime();
        String subcontextName = contextName + ".sub0";
        ContextFactory factory = ContextFactory.getFactory();
        factory.setAttribute(contextName + ".class", CompositeContext.class.getName());
        factory.setAttribute(contextName + ".arity", "1");
        factory.setAttribute(subcontextName + ".class", NullContext.class.getName());

        MetricsContext context = null;
        try {
            context = factory.getContext(contextName);
            MetricsRecord record = context.createRecord("namenode");

            assertThat(context).isInstanceOf(CompositeContext.class);
            assertThat(record.getRecordName()).isEqualTo("namenode");
        } finally {
            if (context != null) {
                context.close();
            }
            factory.removeAttribute(contextName + ".class");
            factory.removeAttribute(contextName + ".arity");
            factory.removeAttribute(subcontextName + ".class");
        }
    }
}
