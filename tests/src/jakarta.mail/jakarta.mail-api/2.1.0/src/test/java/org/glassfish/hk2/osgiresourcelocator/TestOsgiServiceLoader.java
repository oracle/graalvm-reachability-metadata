/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.glassfish.hk2.osgiresourcelocator;

import java.util.Collections;

public final class TestOsgiServiceLoader extends ServiceLoader {
    @Override
    <T> Iterable<? extends T> lookupProviderInstances1(Class<T> serviceClass, ProviderFactory<T> factory) {
        return Collections.emptyList();
    }

    @Override
    <T> Iterable<Class> lookupProviderClasses1(Class<T> serviceClass) {
        return Collections.emptyList();
    }
}
