/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_slf4j2_impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.slf4j.SLF4JServiceProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.spi.MDCAdapter;

public class Log4j_slf4j2_implTest {
    @Test
    void serviceProviderSuppliesSlf4jFactoriesBackedByLog4j() {
        SLF4JServiceProvider provider = new SLF4JServiceProvider();
        provider.initialize();

        ILoggerFactory loggerFactory = provider.getLoggerFactory();
        IMarkerFactory markerFactory = provider.getMarkerFactory();
        MDCAdapter mdcAdapter = provider.getMDCAdapter();

        assertThat(provider.getRequestedApiVersion()).startsWith("2.");
        assertThat(loggerFactory).isNotNull();
        assertThat(markerFactory).isNotNull();
        assertThat(mdcAdapter).isNotNull();
    }

    @Test
    void markerFactoryCreatesAttachedAndNestedMarkers() {
        String markerSuffix = Long.toHexString(System.nanoTime());
        String parentName = "LOG4J_SLF4J2_IMPL_PARENT_" + markerSuffix;
        String childName = "LOG4J_SLF4J2_IMPL_CHILD_" + markerSuffix;

        Marker parent = MarkerFactory.getMarker(parentName);
        Marker sameParent = MarkerFactory.getMarker(parentName);
        Marker child = MarkerFactory.getMarker(childName);

        parent.add(child);

        assertThat(sameParent).isSameAs(parent);
        assertThat(parent.contains(child)).isTrue();
        assertThat(parent.contains(childName)).isTrue();
        assertThat(child.contains(parent)).isFalse();
        assertThat(MarkerFactory.getIMarkerFactory().exists(parentName)).isTrue();
        assertThat(MarkerFactory.getIMarkerFactory().exists(childName)).isTrue();

        assertThat(parent.remove(child)).isTrue();
        assertThat(parent.contains(child)).isFalse();
        assertThat(MarkerFactory.getIMarkerFactory().detachMarker(parentName)).isFalse();
    }

    @Test
    void parameterizedMessagesPreserveSlf4jThrowableSemantics() {
        Throwable failure = new IllegalArgumentException("expected parameterized exception");

        Message parameterizedMessage = new ParameterizedMessage("Hello, {}", new Object[] {"GraalVM"}, null);
        Message parameterizedWithThrowable = new ParameterizedMessage(
                "{} failed at {}", new Object[] {"operation", "startup"}, failure);
        Message varargsWithThrowable = new ParameterizedMessage(
                "values {} {}", new Object[] {"one", "two", failure}, null);

        assertThat(parameterizedMessage.getFormattedMessage()).isEqualTo("Hello, GraalVM");
        assertThat(parameterizedMessage.getThrowable()).isNull();
        assertThat(parameterizedWithThrowable.getFormattedMessage()).isEqualTo("operation failed at startup");
        assertThat(parameterizedWithThrowable.getThrowable()).isSameAs(failure);
        assertThat(varargsWithThrowable.getFormattedMessage()).isEqualTo("values one two");
        assertThat(varargsWithThrowable.getThrowable()).isSameAs(failure);
    }
}
