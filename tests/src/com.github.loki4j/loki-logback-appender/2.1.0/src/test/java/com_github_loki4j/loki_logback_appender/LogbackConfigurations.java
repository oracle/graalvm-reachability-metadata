/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_loki4j.loki_logback_appender;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/// Builds isolated logback contexts, optionally configured from an XML document.
public final class LogbackConfigurations {

    private LogbackConfigurations() {
    }

    /// Returns a fresh context with its own MDC adapter, independent of the SLF4J binding.
    public static LoggerContext newContext() {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        return context;
    }

    /// Returns a fresh context configured by logback's Joran from the given XML document.
    public static LoggerContext configure(String xml) throws JoranException {
        LoggerContext context = newContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return context;
    }
}
