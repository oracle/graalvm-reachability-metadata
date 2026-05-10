/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_core;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.impl.PipelineSink;
import org.apache.maven.doxia.sink.impl.SinkAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PipelineSinkTest {
    @Test
    void newInstanceCreatesProxyThatBroadcastsSinkCallsToEveryPipelineEntry() {
        List<Sink> pipeline = new ArrayList<>();
        RecordingSink firstSink = new RecordingSink();
        RecordingSink secondSink = new RecordingSink();
        pipeline.add(firstSink);
        pipeline.add(secondSink);

        Sink sink = PipelineSink.newInstance(pipeline);
        sink.body();
        sink.text("content");
        sink.rawText("<strong>raw</strong>");
        sink.body_();

        assertThat(firstSink.events)
                .containsExactly("body", "text:content", "raw:<strong>raw</strong>", "body_");
        assertThat(secondSink.events)
                .containsExactly("body", "text:content", "raw:<strong>raw</strong>", "body_");
    }

    @Test
    void handlerCanAddSinksBeforeCreatingThePipelineProxy() {
        List<Sink> pipeline = new ArrayList<>();
        RecordingSink initialSink = new RecordingSink();
        RecordingSink addedSink = new RecordingSink();
        pipeline.add(initialSink);

        PipelineSink pipelineSink = new PipelineSink(pipeline);
        pipelineSink.addSink(addedSink);

        Sink sink = PipelineSink.newInstance(pipeline);
        sink.flush();
        sink.close();

        assertThat(initialSink.events).containsExactly("flush", "close");
        assertThat(addedSink.events).containsExactly("flush", "close");
    }

    private static final class RecordingSink extends SinkAdapter {
        private final List<String> events = new ArrayList<>();

        @Override
        public void body() {
            events.add("body");
        }

        @Override
        public void body_() {
            events.add("body_");
        }

        @Override
        public void text(String text) {
            events.add("text:" + text);
        }

        @Override
        public void rawText(String text) {
            events.add("raw:" + text);
        }

        @Override
        public void flush() {
            events.add("flush");
        }

        @Override
        public void close() {
            events.add("close");
        }
    }
}
