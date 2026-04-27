/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ivy.util.MemoryUtil;
import org.junit.jupiter.api.Test;

public class MemoryUtilTest {
    @Test
    public void estimatesObjectSizeByInstantiatingTheProvidedClass() {
        MeasuredObject.resetConstructorCalls();

        MemoryUtil.sizeOf(MeasuredObject.class);

        assertThat(MeasuredObject.constructorCalls()).isEqualTo(101);
    }

    @Test
    public void mainLoadsTheClassNameAndPrintsTheEstimatedObjectSize() throws Exception {
        MeasuredObject.resetConstructorCalls();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream replacementOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(replacementOut);

            MemoryUtil.main(new String[] {MeasuredObject.class.getName()});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8).trim()).matches("-?\\d+");
        assertThat(MeasuredObject.constructorCalls()).isEqualTo(101);
    }

    public static class MeasuredObject {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public MeasuredObject() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        public static void resetConstructorCalls() {
            CONSTRUCTOR_CALLS.set(0);
        }

        public static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }
    }
}
