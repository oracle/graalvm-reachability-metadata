/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.transport.LogWriter;
import org.apache.activemq.util.LogWriterFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogWriterFinderTest {

    private static final String LOG_WRITER_PATH = "META-INF/services/org/apache/activemq/transport/logwriters/";

    @Test
    void loadsBundledLogWriterThroughContextClassLoader() throws Exception {
        LogWriter writer = new LogWriterFinder(LOG_WRITER_PATH).newInstance("default");

        assertThat(writer.getClass().getName())
                .isEqualTo("org.apache.activemq.transport.logwriters.DefaultLogWriter");
    }

    @Test
    void fallsBackToLibraryClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());

        try {
            LogWriter writer = new LogWriterFinder(LOG_WRITER_PATH).newInstance("default");

            assertThat(writer.getClass().getName())
                    .isEqualTo("org.apache.activemq.transport.logwriters.DefaultLogWriter");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }
}
