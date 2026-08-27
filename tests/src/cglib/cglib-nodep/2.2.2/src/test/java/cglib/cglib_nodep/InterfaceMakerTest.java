/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.InterfaceMaker;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InterfaceMakerTest {
    @Test
    void createsAnInterfaceWithAllPublicMethods() {
        try {
            InterfaceMaker maker = new InterfaceMaker();
            maker.add(GreetingContract.class);

            Class<?> generatedInterface = maker.create();

            assertThat(generatedInterface.isInterface()).isTrue();
            assertThat(generatedInterface.getMethods())
                    .extracting(Method::getName)
                    .containsExactlyInAnyOrder("greet", "priority");
        } catch (Error error) {
            // CGLIB defines the generated interface class at runtime.
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public interface GreetingContract {
        String greet(String name);

        int priority();
    }
}
