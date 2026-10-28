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

public class ClassInjectorInnerUsingLookupInnerDispatcherInnerForJava9CapableVmTest {
    @Test
    void preservesLookupScopeAfterRestrictingAccess() {
        MethodHandles.Lookup lookup = MethodHandles.lookup().dropLookupMode(MethodHandles.Lookup.PRIVATE);
        ClassInjector.UsingLookup injector = ClassInjector.UsingLookup.of(lookup);
        assertEquals(lookup.lookupClass(), injector.lookupType());
    }
}
