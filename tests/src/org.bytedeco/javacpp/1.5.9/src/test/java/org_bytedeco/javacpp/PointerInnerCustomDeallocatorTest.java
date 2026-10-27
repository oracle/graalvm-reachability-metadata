/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.Pointer;

public class PointerInnerCustomDeallocatorTest {

    public static class ManagedPointer extends Pointer {
        private static long deallocatedAddress;

        public ManagedPointer() {
            address = 41;
        }

        public ManagedPointer(Pointer pointer) {
            super(pointer);
        }

        public static ManagedPointer create() {
            deallocatedAddress = 0;
            return withDeallocator(new ManagedPointer());
        }

        private static void deallocate(ManagedPointer pointer) {
            deallocatedAddress = pointer.address();
        }
    }

    @Test
    public void customDeallocatorInvokesBindingCleanupMethod() {
        ManagedPointer pointer = ManagedPointer.create();

        pointer.deallocate();

        assertThat(ManagedPointer.deallocatedAddress).isEqualTo(41);
        assertThat(pointer.isNull()).isTrue();
    }
}
