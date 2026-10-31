/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginElementVisitorTest {
    @Test
    void injectsMultipleChildFiltersAsATypedArray() throws Exception {
        try (LoggerContext context = new LoggerContext("element-visitor")) {
            XmlConfiguration configuration = TestSupport.configure(context, """
                    <Configuration status="OFF" shutdownHook="disable">
                      <Filters>
                        <ThresholdFilter level="ERROR" onMatch="DENY" onMismatch="NEUTRAL"/>
                        <RegexFilter regex="accepted" onMatch="ACCEPT" onMismatch="DENY"/>
                      </Filters>
                      <Loggers><Root level="OFF"/></Loggers>
                    </Configuration>
                    """);
            Filter filter = configuration.getFilter();
            assertEquals(Filter.Result.ACCEPT, filter.filter(TestSupport.event("accepted")));
            assertEquals(Filter.Result.DENY, filter.filter(TestSupport.event("other")));
            assertEquals(Filter.Result.DENY, filter.filter(Log4jLogEvent.newBuilder()
                    .setLevel(Level.ERROR).setMessage(TestSupport.event("accepted").getMessage()).build()));
        }
    }
}
