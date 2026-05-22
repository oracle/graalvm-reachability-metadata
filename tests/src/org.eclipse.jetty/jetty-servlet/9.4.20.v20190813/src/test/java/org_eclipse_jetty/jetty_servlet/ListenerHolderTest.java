/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ListenerHolder;
import org.junit.jupiter.api.Test;

public class ListenerHolderTest {
    @Test
    public void startCreatesListenerInstanceFromHeldClassInContextHandler() throws Exception {
        ContextHandler contextHandler = new ContextHandler("/");
        ListenerHolder holder = new ListenerHolder(RecordingContextListener.class);

        try {
            runInContext(contextHandler, holder::start);

            assertThat(holder.getListener()).isInstanceOf(RecordingContextListener.class);
            RecordingContextListener listener = (RecordingContextListener) holder.getListener();
            assertThat(listener.isInitialized()).isFalse();
            assertThat(listener.isDestroyed()).isFalse();
            assertThat(contextHandler.getEventListeners()).contains(listener);
        } finally {
            holder.stop();
            contextHandler.destroy();
        }
    }

    private static void runInContext(ContextHandler contextHandler, ThrowingRunnable action) throws Exception {
        Throwable[] failure = new Throwable[1];
        contextHandler.handle(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            }
        });

        if (failure[0] instanceof Exception) {
            throw (Exception) failure[0];
        }
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        }
        if (failure[0] != null) {
            throw new Exception(failure[0]);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static class RecordingContextListener implements ServletContextListener {
        private boolean initialized;
        private boolean destroyed;

        @Override
        public void contextInitialized(ServletContextEvent event) {
            initialized = true;
        }

        @Override
        public void contextDestroyed(ServletContextEvent event) {
            destroyed = true;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isDestroyed() {
            return destroyed;
        }
    }
}
