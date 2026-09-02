/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.transport.LogWriter;
import org.apache.activemq.transport.logwriters.DefaultLogWriter;
import org.apache.activemq.util.LogWriterFinder;
import org.junit.jupiter.api.Test;

public class LogWriterFinderTest {

    @Test
    void loadsDefaultLogWriterFromServiceDescriptor() throws Exception {
        LogWriterFinder finder =
                new LogWriterFinder("META-INF/services/org/apache/activemq/transport/logwriters/");

        LogWriter writer = finder.newInstance("default");
        writer.setPrefix("broker-test: ");

        assertThat(writer).isInstanceOf(DefaultLogWriter.class);
        assertThat(finder.newInstance("default")).isNotSameAs(writer);
    }
}
