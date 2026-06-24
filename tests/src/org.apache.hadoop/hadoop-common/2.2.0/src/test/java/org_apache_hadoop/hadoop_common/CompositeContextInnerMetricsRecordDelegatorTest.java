/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.hadoop.metrics.ContextFactory;
import org.apache.hadoop.metrics.MetricsContext;
import org.apache.hadoop.metrics.MetricsRecord;
import org.apache.hadoop.metrics.spi.CompositeContext;
import org.apache.hadoop.metrics.spi.NullContext;
import org.junit.jupiter.api.Test;

public class CompositeContextInnerMetricsRecordDelegatorTest {
    @Test
    void delegatesRecordMutationsToConfiguredSubcontexts() throws Exception {
        String contextName = "metricsRecordDelegatorTest-" + System.nanoTime();
        String subcontextName = contextName + ".sub0";
        ContextFactory factory = ContextFactory.getFactory();
        factory.setAttribute(contextName + ".class", CompositeContext.class.getName());
        factory.setAttribute(contextName + ".arity", "1");
        factory.setAttribute(subcontextName + ".class", NullContext.class.getName());

        MetricsContext context = null;
        try {
            context = factory.getContext(contextName);
            MetricsRecord record = context.createRecord("namenode");

            assertThatCode(() -> {
                record.setTag("host", "localhost");
                record.setMetric("files", 7);
                record.update();
                record.remove();
            }).doesNotThrowAnyException();
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
