/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr4_runtime;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ANTLR's Unicode character-stream subsystem for a source editor.
public class UnicodeCharStreamTest {

    @Test
    @Timeout(60)
    public void readsUnicodeSourceByCodePoint() throws Exception {
        String source = "route " + "\uD83D\uDE80" + " / " + "\u4E2D";
        CharStream input = CharStreams.fromReader(new StringReader(source), "routes.conf");

        assertEquals("routes.conf", input.getSourceName());
        assertEquals(11, input.size());
        assertEquals('r', input.LA(1));
        assertEquals(source, input.getText(Interval.of(0, input.size() - 1)));

        int marker = input.mark();
        input.seek(6);
        assertEquals("\uD83D\uDE80", input.getText(Interval.of(input.index(), input.index())));
        assertEquals("\uD83D\uDE80", Character.toString(input.LA(1)));
        input.consume();
        assertEquals(7, input.index());
        assertEquals(" / \u4E2D", input.getText(Interval.of(input.index(), input.size() - 1)));
        input.release(marker);

        input.seek(input.size());
        assertEquals(IntStream.EOF, input.LA(1));
    }
}
