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
    void capturesAndFormatsCurrentThreadDump() {
        String title = "thread dump coverage";
        String currentThreadName = Thread.currentThread().getName();

        ThreadDumpMessage message = new ThreadDumpMessage(title);
        String formattedMessage = message.getFormattedMessage();

        assertThat(message.getFormat()).isEqualTo(title);
        assertThat(message.getParameters()).isNull();
        assertThat(message.getThrowable()).isNull();
        assertThat(message.toString()).isEqualTo("ThreadDumpMessage[Title=\"" + title + "\"]");
        assertThat(formattedMessage)
            .startsWith(title + "\n")
            .contains('"' + currentThreadName + '"');
    }

    @Test
    void treatsNullTitleAsEmptyString() {
        ThreadDumpMessage message = new ThreadDumpMessage(null);

        assertThat(message.getFormat()).isEmpty();
        assertThat(message.toString()).isEqualTo("ThreadDumpMessage[]");
        assertThat(message.getFormattedMessage()).doesNotStartWith("\n");
    }
}
