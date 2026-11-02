/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractStyleNameConverterTest {
    @Test
    void stylesMessagesUsingTheNamedColorConverters() {
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%red{%m}|%blue{%p}")
                .withDisableAnsi(false).withNoConsoleNoAnsi(false).build();
        assertEquals("\u001b[31mhello\u001b[m|\u001b[34mINFO\u001b[m",
                layout.toSerializable(TestSupport.event("hello")));
    }
}
