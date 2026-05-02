/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.hk2.osgiresourcelocator;

import java.util.Collections;

import jakarta.xml.bind.JAXBContextFactory;
import jakarta_xml_bind.jakarta_xml_bind_api.support.FactoryBackedContextFactory;

public final class ServiceLoader {
    private ServiceLoader() {
    }

    public static <T> Iterable<Class<? extends T>> lookupProviderClasses(Class<T> serviceClass) {
        if (serviceClass != JAXBContextFactory.class) {
            return Collections.emptyList();
        }
        return Collections.singletonList(FactoryBackedContextFactory.class.asSubclass(serviceClass));
    }
}
