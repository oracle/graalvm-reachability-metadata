/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.util.LoaderUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderUtilTest {

    @Test
    void constructsMessageFactoryByImplementationName() throws Exception {
        MessageFactory factory = LoaderUtil.newCheckedInstanceOf(
                "org.apache.logging.log4j.message.ParameterizedMessageFactory", MessageFactory.class);

        assertThat(factory.newMessage("Order {} has {} items", "o-9", 3).getFormattedMessage())
                .isEqualTo("Order o-9 has 3 items");
    }

    @Test
    void fallsBackToLibraryLoaderWhenContextLoaderCannotSeeImplementation() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            MessageFactory factory = LoaderUtil.newCheckedInstanceOf(
                    "org.apache.logging.log4j.message.ParameterizedMessageFactory", MessageFactory.class);

            assertThat(factory.newMessage("Shipment {}", "s-8").getFormattedMessage()).isEqualTo("Shipment s-8");
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
