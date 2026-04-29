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

public class ClassInjectorInnerUsingReflectionInnerDispatcherInnerDirectInnerForJava7CapableVmTest {

    @Test
    void directDispatcherObtainsClassLoadingLockForJava7CapableVm() throws Exception {
        UnsafeOverrideDispatcherAccess.Operations dispatcher = UnsafeOverrideDispatcherAccess.createDirect();
        ClassLoader targetClassLoader = new IsolatedClassLoader(getClass().getClassLoader());
        String typeName = "org_modelmapper.modelmapper.direct.lock.DirectReflectionDispatcherLockedType";

        Object firstLock = dispatcher.getClassLoadingLock(targetClassLoader, typeName);
        Object secondLock = dispatcher.getClassLoadingLock(targetClassLoader, typeName);

        assertThat(firstLock).isNotNull();
        assertThat(secondLock).isSameAs(firstLock);
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        IsolatedClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
