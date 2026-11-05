/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.util.List;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import javassist.util.HotSwapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(30)
public class HotSwapperTest {
    @Test
    void rejectsANegativeNumericDebuggerPort() {
        IllegalConnectorArgumentsException failure = assertThrows(IllegalConnectorArgumentsException.class,
                () -> new HotSwapper(-1));
        assertEquals(List.of("port"), failure.argumentNames());
    }

    @Test
    void rejectsANonnumericDebuggerPort() {
        IllegalConnectorArgumentsException failure = assertThrows(IllegalConnectorArgumentsException.class,
                () -> new HotSwapper("not-a-port"));
        assertEquals(List.of("port"), failure.argumentNames());
    }
}
