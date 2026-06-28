/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.message.ThreadDumpMessage;
import org.junit.jupiter.api.Test;

public class ThreadDumpMessageTest {
    @Test
    void capturesThreadDumpWithTitle() {
        String title = "diagnostic thread dump";

        ThreadDumpMessage message = new ThreadDumpMessage(title);

        assertThat(message.getFormat()).isEqualTo(title);
        assertThat(message.toString()).contains("Title=\"" + title + "\"");
        assertThat(message.getParameters()).isNull();
        assertThat(message.getThrowable()).isNull();
    }

    @Test
    void normalizesNullTitleToEmptyFormat() {
        ThreadDumpMessage message = new ThreadDumpMessage(null);

        assertThat(message.getFormat()).isEmpty();
        assertThat(message.toString()).isEqualTo("ThreadDumpMessage[]");
    }
}
