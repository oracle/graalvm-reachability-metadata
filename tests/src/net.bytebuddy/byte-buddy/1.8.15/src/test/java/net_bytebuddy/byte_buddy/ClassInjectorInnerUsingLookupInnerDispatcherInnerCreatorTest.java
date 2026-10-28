/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.invoke.MethodHandles;

import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClassInjectorInnerUsingLookupInnerDispatcherInnerCreatorTest {
    @Test
    void acceptsPackageLookupAndRejectsPublicOnlyLookup() {
        ClassInjector.UsingLookup injector = ClassInjector.UsingLookup.of(MethodHandles.lookup());
        assertEquals(getClass(), injector.lookupType());
        assertThrows(IllegalArgumentException.class, () -> ClassInjector.UsingLookup.of(MethodHandles.publicLookup()));
    }
}
