/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JreMemoryLeakPreventionListenerTest {
    private static final AtomicBoolean initialized = new AtomicBoolean();

    @Test
    void eagerlyInitializesConfiguredApplicationClassAndRestoresLoader() {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        JreMemoryLeakPreventionListener listener = new JreMemoryLeakPreventionListener();
        listener.setClassesToInitialize("tomcat.JreMemoryLeakPreventionListenerTest$EagerComponent");
        listener.lifecycleEvent(new LifecycleEvent(new StandardServer(), Lifecycle.BEFORE_INIT_EVENT, null));
        assertThat(initialized).isTrue();
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(previous);
    }

    public static class EagerComponent {
        static {
            initialized.set(true);
        }
    }
}
