/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.message.TraceSystem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class TraceSystemTest {
    @Test
    void adapterTraceLevelConstructsTraceWriterAdapter() throws Exception {
        Path traceFile = Files.createTempFile("h2-trace-adapter", ".trace.db");
        TraceSystem traceSystem = new TraceSystem(traceFile.toString());
        try {
            assertThatCode(() -> traceSystem.setLevelFile(TraceSystem.ADAPTER)).doesNotThrowAnyException();
            assertThat(traceSystem.getLevelFile()).isEqualTo(TraceSystem.ADAPTER);
        } finally {
            traceSystem.close();
            Files.deleteIfExists(traceFile);
        }
    }
}
