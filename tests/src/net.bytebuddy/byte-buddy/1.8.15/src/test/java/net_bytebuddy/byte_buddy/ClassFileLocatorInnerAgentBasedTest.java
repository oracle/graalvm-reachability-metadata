/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.reflect.InvocationTargetException;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassFileLocatorInnerAgentBasedTest {
    @Test
    void requiresAnInstalledAgentBeforeLocatingClassFiles() {
        ClassLoader loader = getClass().getClassLoader();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ClassFileLocator.AgentBased.fromInstalledAgent(loader));

        assertThat(exception).hasMessage("The Byte Buddy agent is not installed or not accessible")
                .hasCauseInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("The Byte Buddy agent is not loaded or this method is not called via the system class loader");
    }
}
