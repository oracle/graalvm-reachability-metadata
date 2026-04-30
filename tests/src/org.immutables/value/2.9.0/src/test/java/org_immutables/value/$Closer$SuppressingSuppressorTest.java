/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.immutables.value.internal.$guava$.io.$CharStreams;
import org.immutables.value.internal.$guava$.io.$LineProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharStreamsLineProcessorTest {

    @Test
    void readLinesStopsWhenProcessorReturnsFalse() throws IOException {
        CapturingLineProcessor processor = new CapturingLineProcessor(2);

        List<String> processedLines = $CharStreams.readLines(new StringReader("alpha\nbeta\ngamma\n"), processor);

        assertThat(processedLines).containsExactly("alpha", "beta");
        assertThat(processor.seenLines()).containsExactly("alpha", "beta");
    }

    private static final class CapturingLineProcessor implements $LineProcessor<List<String>> {
        private final int maximumLines;
        private final List<String> seenLines = new ArrayList<>();

        private CapturingLineProcessor(int maximumLines) {
            this.maximumLines = maximumLines;
        }

        @Override
        public boolean processLine(String line) {
            seenLines.add(line);
            return seenLines.size() < maximumLines;
        }

        @Override
        public List<String> getResult() {
            return List.copyOf(seenLines);
        }

        private List<String> seenLines() {
            return List.copyOf(seenLines);
        }
    }
}
