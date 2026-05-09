/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.Test;

public class WebAppContextTest {
    @Test
    void preConfigureInstantiatesConfiguredConfigurationClass() throws Exception {
        RecordingConfiguration.reset();
        WebAppContext context = new WebAppContext();
        context.setClassLoader(WebAppContextTest.class.getClassLoader());
        context.setConfigurationClasses(new String[] {RecordingConfiguration.class.getName()});

        try {
            context.preConfigure();

            assertThat(context.getConfigurations()).hasSize(1);
            assertThat(context.getConfigurations()[0]).isInstanceOf(RecordingConfiguration.class);
            assertThat(context.getAttribute(RecordingConfiguration.PRE_CONFIGURE_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
            assertThat(RecordingConfiguration.constructorCalls()).hasValue(1);
        } finally {
            context.destroy();
        }
    }

    public static class RecordingConfiguration implements Configuration {
        static final String PRE_CONFIGURE_ATTRIBUTE = RecordingConfiguration.class.getName() + ".preConfigure";

        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        public RecordingConfiguration() {
            CONSTRUCTOR_CALLS.incrementAndGet();
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static AtomicInteger constructorCalls() {
            return CONSTRUCTOR_CALLS;
        }

        @Override
        public void preConfigure(WebAppContext context) {
            context.setAttribute(PRE_CONFIGURE_ATTRIBUTE, Boolean.TRUE);
        }

        @Override
        public void configure(WebAppContext context) {
        }

        @Override
        public void postConfigure(WebAppContext context) {
        }

        @Override
        public void deconfigure(WebAppContext context) {
        }

        @Override
        public void destroy(WebAppContext context) {
        }

        @Override
        public void cloneConfigure(WebAppContext template, WebAppContext context) {
        }
    }
}
