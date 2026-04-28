/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.handler.codec.serialization.ClassResolver;
import io.netty.handler.codec.serialization.ClassResolvers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClassLoaderClassResolverTest {
    @Test
    void resolvesBinaryClassNamesWithConfiguredClassLoader() throws ClassNotFoundException {
        ClassResolver resolver = newResolver();

        Class<?> resolvedClass = resolver.resolve(String.class.getName());

        Assertions.assertSame(String.class, resolvedClass);
    }

    @Test
    void fallsBackToClassForNameForArrayDescriptors() throws ClassNotFoundException {
        ClassResolver resolver = ClassResolvers.cacheDisabled(new ArrayDescriptorFallbackClassLoader());

        Class<?> resolvedClass = resolver.resolve(int[].class.getName());

        Assertions.assertSame(int[].class, resolvedClass);
    }

    private static ClassResolver newResolver() {
        return ClassResolvers.cacheDisabled(ClassLoaderClassResolverTest.class.getClassLoader());
    }

    private static final class ArrayDescriptorFallbackClassLoader extends ClassLoader {
        private ArrayDescriptorFallbackClassLoader() {
            super(ClassLoaderClassResolverTest.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (int[].class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
