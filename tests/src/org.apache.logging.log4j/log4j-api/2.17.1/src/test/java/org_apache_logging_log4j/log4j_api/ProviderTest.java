/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.logging.log4j.simple.SimpleLoggerContext;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.Provider;
import org.apache.logging.log4j.spi.ThreadContextMap;
import org.apache.logging.log4j.util.LoaderUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProviderTest {

    @Test
    void loadsUsableFactoryAndContextMapFromProviderDescriptor() throws Exception {
        URL descriptor = ProviderTest.class.getResource("/META-INF/log4j-provider.properties");
        assertThat(descriptor).isNotNull();
        Properties properties = new Properties();
        try (InputStream input = descriptor.openStream()) {
            properties.load(input);
        }
        Provider provider = new Provider(properties, descriptor, ProviderTest.class.getClassLoader());

        LoggerContextFactory factory = LoaderUtil.newInstanceOf(provider.loadLoggerContextFactory());
        LoggerContext context = factory.getContext(ProviderTest.class.getName(), null, null, false);
        ThreadContextMap map = LoaderUtil.newInstanceOf(provider.loadThreadContextMap());
        try {
            map.put("request", "r-51");

            assertThat(provider.getPriority()).isEqualTo(10);
            assertThat(context).isInstanceOf(SimpleLoggerContext.class);
            assertThat(context.getLogger("provider-orders").getMessageFactory()
                    .newMessage("Order {}", "o-51").getFormattedMessage()).isEqualTo("Order o-51");
            assertThat(map.get("request")).isEqualTo("r-51");
            map.remove("request");
            assertThat(map.isEmpty()).isTrue();
        } finally {
            map.clear();
            factory.removeContext(context);
        }
    }
}
