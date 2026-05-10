/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.ibm.ws.webcontainer;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public final class WebContainer {
    private static final String REMOVE_TRAILING_SLASH_PROPERTY =
            "com.ibm.ws.webcontainer.removetrailingservletpathslash";

    private static final AtomicInteger PROPERTIES_INVOCATION_COUNT = new AtomicInteger();

    private WebContainer() {
    }

    public static Properties getWebContainerProperties() {
        PROPERTIES_INVOCATION_COUNT.incrementAndGet();
        Properties properties = new Properties();
        properties.setProperty(REMOVE_TRAILING_SLASH_PROPERTY, "false");
        return properties;
    }

    public static void reset() {
        PROPERTIES_INVOCATION_COUNT.set(0);
    }

    public static int getPropertiesInvocationCount() {
        return PROPERTIES_INVOCATION_COUNT.get();
    }
}
