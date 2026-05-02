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

class CloserSuppressingSuppressorTest {

    @Test
    void readLinesStopsWhenProcessorReturnsFalse() throws IOException {
        List<String> processedLines = new ArrayList<>();
        $LineProcessor<List<String>> processor = new $LineProcessor<>() {
            @Override
            public boolean processLine(String line) {
                processedLines.add(line);
                return !"second".equals(line);
            }

            @Override
            public List<String> getResult() {
                return processedLines;
            }
        };

        List<String> result = $CharStreams.readLines(new StringReader("first\nsecond\nthird"), processor);

        assertThat(result).isSameAs(processedLines);
        assertThat(processedLines).containsExactly("first", "second");
    }
}
