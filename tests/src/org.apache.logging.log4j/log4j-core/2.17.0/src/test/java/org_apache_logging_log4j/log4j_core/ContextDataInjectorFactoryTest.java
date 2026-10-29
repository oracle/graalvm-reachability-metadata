/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.List;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.ContextDataInjectorFactory;
import org.apache.logging.log4j.core.impl.ThreadContextDataInjector;
import org.apache.logging.log4j.util.StringMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class ContextDataInjectorFactoryTest {
    @Test
    void selectsACustomInjectorAndCombinesContextWithConfiguredProperties() {
        ContextDataInjector injector = TestSupport.withProperty("log4j2.ContextDataInjector",
                ApplicationInjector.class.getName(), ContextDataInjectorFactory::createInjector);
        assertInstanceOf(ApplicationInjector.class, injector);
        try (CloseableThreadContext.Instance ignored = CloseableThreadContext.put("request", "req-42")) {
            StringMap values = injector.injectContextData(List.of(Property.createProperty("region", "west")),
                    ContextDataFactory.createContextData());
            assertEquals("req-42", values.getValue("request"));
            assertEquals("west", values.getValue("region"));
        }
    }

    public static class ApplicationInjector extends ThreadContextDataInjector.ForDefaultThreadContextMap {
    }
}
