/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.openejb.javaee.api.support;

import java.util.Map;

import javax.xml.bind.JAXBContext;

public final class ThreeArgumentContextFactory {
    private ThreeArgumentContextFactory() {
    }

    public static JAXBContext createContext(String contextPath, ClassLoader classLoader, Map<?, ?> properties) {
        return new StubJaxbContext("three-argument-factory");
    }
}
