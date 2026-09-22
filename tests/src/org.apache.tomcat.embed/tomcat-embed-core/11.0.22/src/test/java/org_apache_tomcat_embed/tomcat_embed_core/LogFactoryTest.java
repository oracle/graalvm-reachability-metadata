/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Order(1)
public class LogFactoryTest {

    @Test
    void createsServiceProvidedLoggerForRequestedName() {
        Log log = LogFactory.getLog("service-provided-logger");

        assertThat(log).isInstanceOf(RecordingLog.class);
        RecordingLog recordingLog = (RecordingLog) log;
        IllegalStateException cause = new IllegalStateException("recorded cause");

        log.warn("recorded message", cause);

        assertThat(recordingLog.getName()).isEqualTo("service-provided-logger");
        assertThat(recordingLog.getLastMessage()).isEqualTo("recorded message");
        assertThat(recordingLog.getLastThrowable()).isSameAs(cause);
    }
}
