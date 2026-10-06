/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.JreMemoryLeakPreventionListener;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JreMemoryLeakPreventionListenerTest {

    @Test
    void initializesConfiguredContainerClassBeforeServerStartup() {
        JreMemoryLeakPreventionListener listener = new JreMemoryLeakPreventionListener();
        listener.setAppContextProtection(false);
        listener.setUrlCacheProtection(false);
        listener.setDriverManagerProtection(false);
        listener.setClassesToInitialize(Tomcat.class.getName());
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();

        listener.lifecycleEvent(new LifecycleEvent(new StandardServer(), Lifecycle.BEFORE_INIT_EVENT, null));

        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(originalLoader);
    }
}
