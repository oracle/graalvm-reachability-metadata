/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.AutoIndentWriter;
import org.stringtemplate.v4.ST;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InterpreterTest {
    @Test
    void parenthesizedExpressionIsRenderedThroughClonedWriter() throws Exception {
        ConstructorTrackingWriter.resetConstructorCalls();
        final ST template = new ST("Total: <(amount)>");
        template.add("amount", 7);
        final StringWriter output = new StringWriter();
        final ConstructorTrackingWriter writer = new ConstructorTrackingWriter(output);

        final int charactersWritten = template.write(writer);

        assertEquals("Total: 7", output.toString());
        assertEquals(output.toString().length(), charactersWritten);
        assertEquals(2, ConstructorTrackingWriter.getConstructorCalls());
    }

    public static final class ConstructorTrackingWriter extends AutoIndentWriter {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public ConstructorTrackingWriter(Writer writer) {
            super(writer);
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void resetConstructorCalls() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int getConstructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }
}
