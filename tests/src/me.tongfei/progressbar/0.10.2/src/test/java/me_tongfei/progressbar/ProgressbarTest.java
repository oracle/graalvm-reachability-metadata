/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package me_tongfei.progressbar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarConsumer;
import me.tongfei.progressbar.ProgressBarStyle;
import me.tongfei.progressbar.ProgressState;
import me.tongfei.progressbar.wrapped.ProgressBarWrappedInputStream;
import me.tongfei.progressbar.wrapped.ProgressBarWrappedIterator;
import me.tongfei.progressbar.wrapped.ProgressBarWrappedOutputStream;
import me.tongfei.progressbar.wrapped.ProgressBarWrappedReader;
import me.tongfei.progressbar.wrapped.ProgressBarWrappedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ProgressbarTest {
    private static final int RENDER_WIDTH = 160;

    @Test
    void progressBarTracksStateMutationsAndElapsedConfiguration() {
        ProgressBar progressBar = newBuilder("state", 10)
                .startsFrom(2, Duration.ofSeconds(7))
                .build();
        try {
            assertThat(progressBar.getTaskName()).isEqualTo("state");
            assertThat(progressBar.getStart()).isEqualTo(2);
            assertThat(progressBar.getCurrent()).isEqualTo(2);
            assertThat(progressBar.getMax()).isEqualTo(10);
            assertThat(progressBar.getNormalizedProgress()).isEqualTo(0.2d);
            assertThat(progressBar.getElapsedBeforeStart()).isEqualTo(Duration.ofSeconds(7));
            assertThat(progressBar.getStartInstant()).isNotNull();
            assertThat(progressBar.getTotalElapsed()).isGreaterThanOrEqualTo(Duration.ofSeconds(7));
            assertThat(progressBar.isIndefinite()).isFalse();

            assertThat(progressBar.step()).isSameAs(progressBar);
            assertThat(progressBar.getCurrent()).isEqualTo(3);

            progressBar.stepBy(4).stepTo(15);
            assertThat(progressBar.getCurrent()).isEqualTo(15);
            assertThat(progressBar.getMax()).isEqualTo(15);
            assertThat(progressBar.getNormalizedProgress()).isEqualTo(1.0d);

            progressBar.maxHint(-1);
            assertThat(progressBar.isIndefinite()).isTrue();
            progressBar.maxHint(20);
            assertThat(progressBar.isIndefinite()).isFalse();
            assertThat(progressBar.getMax()).isEqualTo(20);

            progressBar.setExtraMessage(" complete");
            assertThat(progressBar.getExtraMessage()).isEqualTo(" complete");

            progressBar.pause();
            assertThat(progressBar.getElapsedBeforeStart()).isGreaterThanOrEqualTo(Duration.ofSeconds(7));
            progressBar.resume();
            assertThat(progressBar.getStartInstant()).isNotNull();

            progressBar.reset();
            assertThat(progressBar.getStart()).isZero();
            assertThat(progressBar.getCurrent()).isZero();
            assertThat(progressBar.getElapsedBeforeStart()).isEqualTo(Duration.ZERO);
        } finally {
            progressBar.close();
        }
    }

    @Test
    void rendersWithCustomStyleConsumerAndBuilderOptions() {
        CapturingProgressBarConsumer consumer = new CapturingProgressBarConsumer(RENDER_WIDTH);
        ProgressBarStyle style = ProgressBarStyle.builder()
                .leftBracket("[[")
                .delimitingSequence("|")
                .rightBracket("]]")
                .block('#')
                .space('-')
                .fractionSymbols(">")
                .rightSideFractionSymbol('?')
                .build();

        ProgressBar progressBar = ProgressBar.builder()
                .setTaskName("rendering")
                .setInitialMax(10)
                .setStyle(style)
                .setConsumer(consumer)
                .setUpdateIntervalMillis(10_000)
                .continuousUpdate()
                .setUnit("items", 1)
                .showSpeed()
                .hideEta()
                .setSpeedUnit(ChronoUnit.SECONDS)
                .startsFrom(0, Duration.ofSeconds(2))
                .build();
        try {
            progressBar.stepTo(5).setExtraMessage(" half-way").refresh();

            assertThat(consumer.lastRendered())
                    .contains("rendering")
                    .contains("50%")
                    .contains("[[")
                    .contains("]]")
                    .contains("5/10items")
                    .contains("half-way");
        } finally {
            progressBar.close();
        }
    }

    @Test
    void rejectsConflictingAnsiColorConfigurationInStyleBuilder() {
        assertThatThrownBy(() -> ProgressBarStyle.builder()
                .leftBracket("\u001b[31m[")
                .colorCode((byte) 32)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("color code is overridden");
    }

    @Test
    void rendersStyleWithConfiguredAnsiColorCode() {
        CapturingProgressBarConsumer consumer = new CapturingProgressBarConsumer(RENDER_WIDTH);
        ProgressBarStyle style = ProgressBarStyle.builder()
                .leftBracket("[")
                .rightBracket("]")
                .colorCode((byte) 36)
                .build();

        ProgressBar progressBar = newBuilder("colored", 2)
                .setStyle(style)
                .setConsumer(consumer)
                .build();
        try {
            progressBar.stepTo(1).refresh();

            assertThat(consumer.lastRendered())
                    .contains("\u001b[36m[")
                    .contains("]\u001b[0m")
                    .contains("50%");
        } finally {
            progressBar.close();
        }
    }

    @Test
    void customRendererReceivesProgressStateAndConsumerReceivesRenderedOutput() {
        CapturingProgressBarConsumer consumer = new CapturingProgressBarConsumer(RENDER_WIDTH);
        List<String> observedStates = new ArrayList<>();
        ProgressBar progressBar = ProgressBar.builder()
                .setTaskName("render-callback")
                .setInitialMax(8)
                .setUpdateIntervalMillis(10_000)
                .continuousUpdate()
                .setConsumer(consumer)
                .setRenderer((ProgressState state, int maxLength) -> {
                    observedStates.add(state.getTaskName() + ":" + state.getCurrent() + ":" + state.getMax()
                            + ":" + state.getExtraMessage() + ":" + maxLength);
                    return "rendered-" + state.getCurrent() + "-of-" + state.getMax();
                })
                .build();
        try {
            progressBar.stepBy(3).setExtraMessage(" ready").refresh();

            assertThat(observedStates)
                    .anyMatch(value -> value.equals("render-callback:3:8: ready:" + RENDER_WIDTH));
            assertThat(consumer.lastRendered()).isEqualTo("rendered-3-of-8");
        } finally {
            progressBar.close();
        }
    }

    @Test
    void customEtaFunctionControlsRenderedEta() {
        CapturingProgressBarConsumer consumer = new CapturingProgressBarConsumer(RENDER_WIDTH);
        List<String> observedStates = new ArrayList<>();
        ProgressBar progressBar = ProgressBar.builder()
                .setTaskName("custom-eta")
                .setInitialMax(4)
                .setUpdateIntervalMillis(10_000)
                .continuousUpdate()
                .setConsumer(consumer)
                .setEtaFunction(state -> {
                    observedStates.add(state.getTaskName() + ":" + state.getCurrent() + ":" + state.getMax());
                    return Optional.of(Duration.ofSeconds(125));
                })
                .build();
        try {
            progressBar.stepTo(1).refresh();

            assertThat(observedStates).contains("custom-eta:1:4");
            assertThat(consumer.lastRendered()).contains(" / 0:02:05");
        } finally {
            progressBar.close();
        }
    }

    @Test
    void wrapsIteratorsIterablesSpliteratorsStreamsAndArrays() throws Exception {
        Iterator<String> iterator = ProgressBar.wrap(
                new ArrayList<>(Arrays.asList("a", "b", "c")).iterator(),
                newBuilder("iterator", 3)
        );
        ProgressBarWrappedIterator<String> wrappedIterator = (ProgressBarWrappedIterator<String>) iterator;
        try {
            assertThat(wrappedIterator.next()).isEqualTo("a");
            assertThat(wrappedIterator.getProgressBar().getCurrent()).isEqualTo(1);
            assertThat(wrappedIterator.next()).isEqualTo("b");
            assertThat(wrappedIterator.next()).isEqualTo("c");
            assertThat(wrappedIterator.hasNext()).isFalse();
            assertThat(wrappedIterator.getProgressBar().getCurrent()).isEqualTo(3);
        } finally {
            wrappedIterator.close();
        }

        Iterable<Integer> iterable = ProgressBar.wrap(Arrays.asList(1, 2, 3), newBuilder("iterable", -1));
        List<Integer> values = new ArrayList<>();
        for (Integer value : iterable) {
            values.add(value);
        }
        assertThat(values).containsExactly(1, 2, 3);

        Spliterator<Integer> spliterator = ProgressBar.wrap(
                Arrays.asList(4, 5, 6).spliterator(),
                newBuilder("spliterator", -1)
        );
        List<Integer> splitValues = new ArrayList<>();
        spliterator.forEachRemaining(splitValues::add);
        assertThat(splitValues).containsExactly(4, 5, 6);

        try (Stream<String> stream = ProgressBar.wrap(Stream.of("x", "y"), newBuilder("stream", -1))) {
            assertThat(stream.collect(Collectors.toList())).containsExactly("x", "y");
        }

        try (Stream<String> arrayStream = ProgressBar.wrap(new String[] {"left", "right"}, newBuilder("array", -1))) {
            assertThat(arrayStream.collect(Collectors.toList())).containsExactly("left", "right");
        }
    }

    @Test
    void wrapsInputStreamsAndTracksReadSkipMarkAndReset() throws IOException {
        byte[] bytes = "abcde".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = ProgressBar.wrap(
                new ByteArrayInputStream(bytes),
                newBuilder("input", -1)
        );
        ProgressBarWrappedInputStream wrappedInputStream = (ProgressBarWrappedInputStream) inputStream;

        try (ProgressBarWrappedInputStream input = wrappedInputStream) {
            byte[] buffer = new byte[2];
            assertThat(input.read(buffer)).isEqualTo(2);
            assertThat(input.getProgressBar().getCurrent()).isEqualTo(2);
            assertThat(input.getProgressBar().getMax()).isEqualTo(bytes.length);

            input.mark(10);
            assertThat(input.read()).isEqualTo((int) 'c');
            assertThat(input.getProgressBar().getCurrent()).isEqualTo(3);

            input.reset();
            assertThat(input.getProgressBar().getCurrent()).isEqualTo(2);

            assertThat(input.skip(2)).isEqualTo(2);
            assertThat(input.getProgressBar().getCurrent()).isEqualTo(4);
            assertThat(input.read()).isEqualTo((int) 'e');
            assertThat(input.read()).isEqualTo(-1);
            assertThat(input.getProgressBar().getCurrent()).isEqualTo(5);
        }
    }

    @Test
    void wrapsOutputStreamsReadersAndWriters() throws IOException {
        ByteArrayOutputStream byteSink = new ByteArrayOutputStream();
        OutputStream outputStream = ProgressBar.wrap(byteSink, newBuilder("output", -1));
        ProgressBarWrappedOutputStream wrappedOutputStream = (ProgressBarWrappedOutputStream) outputStream;
        try (ProgressBarWrappedOutputStream output = wrappedOutputStream) {
            output.write('A');
            output.write("BCD".getBytes(StandardCharsets.UTF_8));
            output.write("EFGH".getBytes(StandardCharsets.UTF_8), 1, 2);
            output.flush();
            assertThat(byteSink.toString(StandardCharsets.UTF_8.name())).isEqualTo("ABCDFG");
            assertThat(output.getProgressBar().getCurrent()).isEqualTo(6);
        }

        Reader reader = ProgressBar.wrap(new StringReader("reader"), newBuilder("reader", 6));
        ProgressBarWrappedReader wrappedReader = (ProgressBarWrappedReader) reader;
        try (ProgressBarWrappedReader trackedReader = wrappedReader) {
            char[] chars = new char[3];
            assertThat(trackedReader.read(chars)).isEqualTo(3);
            assertThat(trackedReader.getProgressBar().getCurrent()).isEqualTo(3);
            trackedReader.mark(10);
            assertThat(trackedReader.read()).isEqualTo((int) 'd');
            trackedReader.reset();
            assertThat(trackedReader.getProgressBar().getCurrent()).isEqualTo(3);
            assertThat(trackedReader.skip(3)).isEqualTo(3);
            assertThat(trackedReader.getProgressBar().getCurrent()).isEqualTo(6);
        }

        StringWriter stringSink = new StringWriter();
        Writer writer = ProgressBar.wrap(stringSink, newBuilder("writer", -1));
        ProgressBarWrappedWriter wrappedWriter = (ProgressBarWrappedWriter) writer;
        try (ProgressBarWrappedWriter trackedWriter = wrappedWriter) {
            trackedWriter.write('J');
            trackedWriter.write(new char[] {'K', 'L', 'M'}, 1, 2);
            trackedWriter.write("NOP", 1, 2);
            trackedWriter.write("QR");
            trackedWriter.flush();
            assertThat(stringSink.toString()).isEqualTo("JLMOPQR");
        }
    }

    @Test
    void delegatingConsumerSupportsAppendClearAndExplicitLength() throws IOException {
        List<String> accepted = new ArrayList<>();
        ProgressBarConsumer consumer = new DelegatingProgressBarConsumer(accepted::add, 12);

        assertThat(consumer.getMaxRenderedLength()).isEqualTo(12);
        assertThat(consumer.append("abc")).isSameAs(consumer);
        assertThat(consumer.append("012345", 2, 5)).isSameAs(consumer);
        assertThat(consumer.append('Z')).isSameAs(consumer);
        consumer.clear();
        consumer.close();

        assertThat(accepted)
                .containsExactly("abc", "234", "Z", "\r            \r");
    }

    private static ProgressBarBuilder newBuilder(String taskName, long initialMax) {
        return ProgressBar.builder()
                .setTaskName(taskName)
                .setInitialMax(initialMax)
                .setConsumer(new CapturingProgressBarConsumer(RENDER_WIDTH))
                .setUpdateIntervalMillis(10_000)
                .continuousUpdate();
    }

    private static final class CapturingProgressBarConsumer implements ProgressBarConsumer {
        private final int maxRenderedLength;
        private final List<String> rendered = new ArrayList<>();

        private CapturingProgressBarConsumer(int maxRenderedLength) {
            this.maxRenderedLength = maxRenderedLength;
        }

        @Override
        public int getMaxRenderedLength() {
            return maxRenderedLength;
        }

        @Override
        public synchronized void accept(String value) {
            rendered.add(value);
        }

        @Override
        public void close() {
            // In-memory consumer; no resource needs closing.
        }

        private synchronized String lastRendered() {
            assertThat(rendered).isNotEmpty();
            return rendered.get(rendered.size() - 1);
        }
    }
}
