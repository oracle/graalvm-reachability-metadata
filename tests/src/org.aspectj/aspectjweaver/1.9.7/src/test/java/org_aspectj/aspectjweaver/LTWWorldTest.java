/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.weaver.ltw.LTWWorld;
import org.junit.jupiter.api.Test;

public class LTWWorldTest {
    @Test
    void resolvesConcurrentMapImplementationForBootstrapTypeCache() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LTWWorld.class, MethodHandles.lookup());
        MethodHandle makeConcurrentMapClass = lookup.findStatic(
                LTWWorld.class,
                "makeConcurrentMapClass",
                MethodType.methodType(Class.class));

        Class<?> concurrentMapClass = (Class<?>) makeConcurrentMapClass.invoke();

        assertThat(concurrentMapClass).isEqualTo(ConcurrentHashMap.class);
    }
}
