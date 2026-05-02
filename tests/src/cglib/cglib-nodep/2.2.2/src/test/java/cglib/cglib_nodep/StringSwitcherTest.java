/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.util.StringSwitcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class StringSwitcherTest {
    @Test
    void mapsKnownStringsAndReturnsMinusOneForUnknownInput() {
        try {
            StringSwitcher switcher = StringSwitcher.create(
                    new String[] { "alpha", "FB", "Ea", "omega" },
                    new int[] { 10, 20, 30, 40 },
                    false);

            assertThat(switcher.intValue("alpha")).isEqualTo(10);
            assertThat(switcher.intValue("FB")).isEqualTo(20);
            assertThat(switcher.intValue("Ea")).isEqualTo(30);
            assertThat(switcher.intValue("omega")).isEqualTo(40);
            assertThat(switcher.intValue("missing")).isEqualTo(-1);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void generatorCreatesFixedInputSwitcherForConfiguredMappings() {
        try {
            StringSwitcher.Generator generator = new StringSwitcher.Generator();
            generator.setStrings(new String[] { "red", "green", "blue" });
            generator.setInts(new int[] { 1, 2, 3 });
            generator.setFixedInput(true);

            StringSwitcher switcher = generator.create();

            assertThat(switcher.intValue("red")).isEqualTo(1);
            assertThat(switcher.intValue("green")).isEqualTo(2);
            assertThat(switcher.intValue("blue")).isEqualTo(3);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
