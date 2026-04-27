/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.hk2.osgiresourcelocator;

import jakarta.activation.spi.MimeTypeRegistryProvider;
import jakarta_activation.jakarta_activation_api.FactoryFinderTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServiceLoader {
    private static final List<Class<?>> LOOKED_UP_SERVICE_TYPES = new ArrayList<>();

    private ServiceLoader() {
    }

    public static <T> Iterable<T> lookupProviderInstances(Class<T> serviceClass) {
        LOOKED_UP_SERVICE_TYPES.add(serviceClass);
        if (serviceClass.equals(MimeTypeRegistryProvider.class)) {
            return List.of(serviceClass.cast(new FactoryFinderTest.OsgiMimeTypeRegistryProvider()));
        }
        return Collections.emptyList();
    }

    public static List<Class<?>> getLookedUpServiceTypes() {
        return List.copyOf(LOOKED_UP_SERVICE_TYPES);
    }

    public static void reset() {
        LOOKED_UP_SERVICE_TYPES.clear();
    }
}
