/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.spi.DefaultThreadContextMap;
import org.apache.logging.log4j.spi.ThreadContextMap;
import org.apache.logging.log4j.spi.ThreadContextMapFactory;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ThreadContextMapFactoryTest {

    @Test
    void usesContextMapFromMatchingLoggerProvider() {
        String property = "log4j2.threadContextMap";
        String original = System.getProperty(property);
        try {
            System.clearProperty(property);
            PropertiesUtil.getProperties().reload();
            ThreadContextMapFactory.init();
            ThreadContextMap map = ThreadContextMapFactory.createThreadContextMap();
            try {
                assertThat(map).isInstanceOf(DefaultThreadContextMap.class);
                map.put("tenant", "acme");
                map.put("request", "r-52");
                map.remove("request");

                assertThat(map.getCopy()).containsExactly(entry("tenant", "acme"));
                assertThat(map.get("tenant")).isEqualTo("acme");
            } finally {
                map.clear();
            }
        } finally {
            if (original == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, original);
            }
            PropertiesUtil.getProperties().reload();
            ThreadContextMapFactory.init();
        }
    }
}
