/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.dynamic.loading.UnsafeOverrideDispatcherAccess;

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerUnavailableTest {

    @Test
    void unavailableDispatcherFindsAlreadyLoadableClass() {
        UnsafeOverrideDispatcherAccess.Operations dispatcher = UnsafeOverrideDispatcherAccess.createUnavailable();
        ClassLoader classLoader = getClass().getClassLoader();

        Class<?> resolvedType = dispatcher.findClass(classLoader, String.class.getName());

        assertThat(resolvedType).isSameAs(String.class);
    }

    @Test
    void unavailableDispatcherReturnsUndefinedForMissingClass() {
        UnsafeOverrideDispatcherAccess.Operations dispatcher = UnsafeOverrideDispatcherAccess.createUnavailable();
        ClassLoader classLoader = getClass().getClassLoader();

        Class<?> resolvedType = dispatcher.findClass(classLoader, "org_modelmapper.modelmapper.DoesNotExist");

        assertThat(resolvedType).isNull();
    }
}
