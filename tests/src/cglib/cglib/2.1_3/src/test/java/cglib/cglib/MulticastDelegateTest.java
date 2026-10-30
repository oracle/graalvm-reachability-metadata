/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.reflect.MulticastDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class MulticastDelegateTest {
    @Test
    void requiresAnInterfaceRatherThanAConcreteListener() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MulticastDelegate.create(Listener.class))
                .withMessageContaining("is not an interface");
    }

    @Test
    void requiresAnInterfaceWithAtLeastOneMethod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MulticastDelegate.create(EmptyListener.class))
                .withMessageContaining("expecting exactly 1 method");
    }

    @Test
    void requiresAnInterfaceWithOnlyOneMethod() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MulticastDelegate.create(LifecycleListener.class))
                .withMessageContaining("expecting exactly 1 method");
    }

    public static class Listener {
        public void onMessage(String message) {
        }
    }

    public interface EmptyListener {
    }

    public interface LifecycleListener {
        void onStart();

        void onStop();
    }
}
