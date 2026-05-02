/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.sf.cglib.reflect.MulticastDelegate;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MulticastDelegateTest {
    @Test
    void dispatchesVoidCallsToAllTargetsAndSupportsRemoval() {
        try {
            RecordingListener firstListener = new RecordingListener("first");
            RecordingListener secondListener = new RecordingListener("second");
            MulticastDelegate delegate = MulticastDelegate.create(EventListener.class);

            MulticastDelegate withBothListeners = delegate.add(firstListener).add(secondListener);
            ((EventListener) withBothListeners).onEvent("created");

            assertThat(firstListener.events()).containsExactly("first:created");
            assertThat(secondListener.events()).containsExactly("second:created");
            assertThat(withBothListeners.getTargets()).containsExactly(firstListener, secondListener);

            MulticastDelegate withSecondListener = withBothListeners.remove(firstListener);
            ((EventListener) withSecondListener).onEvent("updated");

            assertThat(firstListener.events()).containsExactly("first:created");
            assertThat(secondListener.events()).containsExactly("second:created", "second:updated");
            assertThat(withSecondListener.getTargets()).containsExactly(secondListener);
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
    void returnsResultFromLastTarget() {
        try {
            MulticastDelegate delegate = MulticastDelegate.create(Computation.class);
            delegate = delegate.add(new FixedComputation(3));
            delegate = delegate.add(new FixedComputation(7));

            int result = ((Computation) delegate).compute(5);

            assertThat(result).isEqualTo(12);
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
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public interface EventListener {
        void onEvent(String name);
    }

    public interface Computation {
        int compute(int value);
    }

    public static class RecordingListener implements EventListener {
        private final String name;
        private final List<String> events = new ArrayList<>();

        public RecordingListener(String name) {
            this.name = name;
        }

        @Override
        public void onEvent(String event) {
            events.add(name + ":" + event);
        }

        public List<String> events() {
            return events;
        }
    }

    public static class FixedComputation implements Computation {
        private final int increment;

        public FixedComputation(int increment) {
            this.increment = increment;
        }

        @Override
        public int compute(int value) {
            return value + increment;
        }
    }
}
