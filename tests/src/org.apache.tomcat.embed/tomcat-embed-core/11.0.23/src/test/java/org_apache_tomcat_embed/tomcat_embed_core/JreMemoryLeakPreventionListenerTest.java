/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JreMemoryLeakPreventionListenerTest {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Test
    void initializesConfiguredClassesBeforeServerInitialization() {
        INITIALIZED.set(false);
        JreMemoryLeakPreventionListener listener = new JreMemoryLeakPreventionListener();
        listener.setDriverManagerProtection(false);
        listener.setUrlCacheProtection(false);
        listener.setClassesToInitialize(StartupComponent.class.getName());

        listener.lifecycleEvent(new LifecycleEvent(new StandardServer(), Lifecycle.BEFORE_INIT_EVENT, null));

        assertThat(INITIALIZED).isTrue();
    }

    public static final class StartupComponent {

        static {
            INITIALIZED.set(true);
        }

        private StartupComponent() {
        }
    }
}
