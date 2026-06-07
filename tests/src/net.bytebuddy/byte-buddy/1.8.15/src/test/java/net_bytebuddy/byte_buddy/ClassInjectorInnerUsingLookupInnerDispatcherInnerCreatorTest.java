/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.loading.ClassInjector;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassInjectorInnerUsingLookupInnerDispatcherInnerCreatorTest {
    @Test
    void detectsLookupBasedClassInjectionSupport() {
        assertThat(ClassInjector.UsingLookup.isAvailable()).isTrue();
    }

    @Test
    void createsInjectorFromMethodHandlesLookup() {
        ClassInjector.UsingLookup injector = ClassInjector.UsingLookup.of(MethodHandles.lookup());

        assertThat(injector.lookupType()).isEqualTo(
                ClassInjectorInnerUsingLookupInnerDispatcherInnerCreatorTest.class);
    }
}
