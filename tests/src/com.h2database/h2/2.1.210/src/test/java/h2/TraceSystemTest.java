/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.message.Trace;
import org.h2.message.TraceSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceSystemTest {
    @TempDir
    Path tempDir;

    @Test
    void setLevelFileCreatesSlf4jTraceWriterAdapter() {
        Path traceFile = tempDir.resolve("trace-system.trace.db");
        TraceSystem traceSystem = new TraceSystem(traceFile.toString());

        traceSystem.setLevelFile(TraceSystem.ADAPTER);

        assertThat(traceSystem.getLevelFile()).isEqualTo(TraceSystem.ADAPTER);

        Trace trace = traceSystem.getTrace("adapterCoverage");
        trace.error(null, "message routed through the SLF4J trace writer adapter");
    }
}
