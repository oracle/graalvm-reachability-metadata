/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JreMemoryLeakPreventionListenerTest {

    @Test
    void initializesConfiguredClassesOnLifecycleEvent() {
        JreMemoryLeakPreventionListener listener = new JreMemoryLeakPreventionListener();
        listener.setClassesToInitialize(Tomcat.class.getName());

        listener.lifecycleEvent(new LifecycleEvent(new StandardServer(), Lifecycle.BEFORE_INIT_EVENT, null));

        assertThat(listener.getClassesToInitialize()).contains(Tomcat.class.getName());
    }
}
