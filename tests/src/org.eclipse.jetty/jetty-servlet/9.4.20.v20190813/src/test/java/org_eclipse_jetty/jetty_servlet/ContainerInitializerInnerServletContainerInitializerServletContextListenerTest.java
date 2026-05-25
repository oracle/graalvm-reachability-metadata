/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.listener.ContainerInitializer;
import org.eclipse.jetty.servlet.listener.ContainerInitializer.ServletContainerInitializerServletContextListener;
import org.junit.jupiter.api.Test;

public class ContainerInitializerInnerServletContainerInitializerServletContextListenerTest {
    @Test
    public void contextInitializedLoadsConfiguredClassNamesWithThreadContextClassLoader() {
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        ServletContext servletContext = contextHandler.getServletContext();
        AtomicReference<Set<Class<?>>> startupClasses = new AtomicReference<>();
        AtomicReference<ServletContext> startupContext = new AtomicReference<>();
        AtomicBoolean afterStartupCalled = new AtomicBoolean();
        ServletContainerInitializer initializer = (classes, context) -> {
            startupClasses.set(classes);
            startupContext.set(context);
        };
        ServletContainerInitializerServletContextListener listener = ContainerInitializer.asContextListener(initializer)
                .addClasses(StartupTarget.class.getName())
                .afterStartup(context -> afterStartupCalled.set(context == servletContext));

        listener.contextInitialized(new ServletContextEvent(servletContext));

        assertThat(startupClasses.get()).containsExactly(StartupTarget.class);
        assertThat(startupContext.get()).isSameAs(servletContext);
        assertThat(afterStartupCalled).isTrue();
    }

    public static class StartupTarget {
    }
}
