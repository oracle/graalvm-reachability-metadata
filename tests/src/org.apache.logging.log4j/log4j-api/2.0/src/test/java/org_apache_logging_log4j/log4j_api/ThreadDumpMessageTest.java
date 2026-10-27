/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.message.ThreadDumpMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadDumpMessageTest {

    @Test
    void startsDumpWithTitleLine() {
        ThreadDumpMessage message = new ThreadDumpMessage("Snapshot");

        assertThat(message.getFormat()).isEqualTo("Snapshot");
        assertThat(message.getFormattedMessage()).startsWith("Snapshot\n");
        assertThat(message.getParameters()).isNull();
        assertThat(message).hasToString("ThreadDumpMessage[Title=\"Snapshot\"]");
    }
}
