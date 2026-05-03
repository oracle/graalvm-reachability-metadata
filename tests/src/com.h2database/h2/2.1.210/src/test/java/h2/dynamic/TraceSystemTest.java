/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.message.TraceSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;

public class TraceSystemTest {
    @TempDir
    Path tempDir;

    @Test
    void createsTraceWriterAdapterWhenAdapterLevelIsRequested() {
        TraceSystem traceSystem = new TraceSystem(tempDir.resolve("adapter.trace.db").toString());
        try {
            assertThatCode(() -> traceSystem.setLevelFile(TraceSystem.ADAPTER)).doesNotThrowAnyException();
        } finally {
            traceSystem.close();
        }
    }
}
