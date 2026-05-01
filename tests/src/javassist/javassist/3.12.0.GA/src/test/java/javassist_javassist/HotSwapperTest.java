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
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class HotSwapperTest {
    @Test
    void constructorInitializesTriggerClassBeforeDebuggerAttachmentFails() {
        try {
            new HotSwapper("not-a-port");
            fail("Expected HotSwapper to reject an invalid debugger port");
        } catch (IllegalConnectorArgumentsException | IOException expected) {
            assertThat(expected.getMessage()).isNotBlank();
        } catch (Error error) {
            verifyUnsupportedHotSwapRuntime(error);
        }
    }

    private static void verifyUnsupportedHotSwapRuntime(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
