/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.io.IOException;
import java.util.Collections;

import org.apache.htrace.HTraceConfiguration;
import org.apache.htrace.SpanReceiver;
import org.apache.htrace.SpanReceiverBuilder;
import org.apache.htrace.impl.POJOSpanReceiver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanReceiverBuilderTest {
    @Test
    void buildsConfiguredSpanReceiverFromUnqualifiedClassName() throws IOException {
        HTraceConfiguration configuration = HTraceConfiguration.fromMap(Collections.singletonMap(
                SpanReceiverBuilder.SPAN_RECEIVER_CONF_KEY,
                "POJOSpanReceiver"));

        SpanReceiver receiver = new SpanReceiverBuilder(configuration).build();

        assertThat(receiver).isInstanceOf(POJOSpanReceiver.class);
        assertThat(((POJOSpanReceiver) receiver).getSpans()).isEmpty();
        receiver.close();
    }
}
