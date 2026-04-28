/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.IOException;

import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import javassist.util.HotSwapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HotSwapperTest {
    @Test
    void rejectsInvalidDebuggerPortAfterPreparingHotSwapTrigger() {
        assertThatThrownBy(() -> new HotSwapper("not-a-debugger-port"))
                .isInstanceOfAny(
                        IllegalArgumentException.class,
                        IOException.class,
                        IllegalConnectorArgumentsException.class);
    }
}
