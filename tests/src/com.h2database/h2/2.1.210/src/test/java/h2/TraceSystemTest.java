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

public class TraceSystemTest {
    @Test
    void createsTraceWriterAdapterForAdapterFileLevel() {
        TraceSystem traceSystem = new TraceSystem(null);
        traceSystem.setLevelFile(TraceSystem.ADAPTER);
        traceSystem.getTrace(Trace.DATABASE).info("adapter trace initialized");
        traceSystem.close();
    }
}
