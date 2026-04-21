/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.openejb.javaee.api.support;

import java.util.Arrays;
import java.util.Map;

import javax.xml.bind.JAXBContext;

import org.apache.openejb.javaee.api.servicebound.WrongTypeBoundType;

public final class ServiceContextFactory {
    private ServiceContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader) {
        return new StubJaxbContext("service-context-factory-string");
    }

    public static Object createContext(Class<?>[] classes, Map<?, ?> properties) {
        if (Arrays.asList(classes).contains(WrongTypeBoundType.class)) {
            return new WrongTypeReturnValue();
        }
        return new StubJaxbContext("service-context-factory-classes");
    }
}
