/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReferenceCountUpdaterTest {
    @Test
    void managesReferenceCountsThroughAbstractReferenceCounted() {
        CountingReferenceCounted referenceCounted = new CountingReferenceCounted();

        Assertions.assertEquals(1, referenceCounted.refCnt());
        Assertions.assertFalse(referenceCounted.isDeallocated());

        ReferenceCounted retained = referenceCounted.retain();
        Assertions.assertSame(referenceCounted, retained);
        Assertions.assertSame(referenceCounted, referenceCounted.touch("retain"));
        Assertions.assertSame(referenceCounted, referenceCounted.retain(2));
        Assertions.assertEquals(4, referenceCounted.refCnt());

        Assertions.assertFalse(referenceCounted.release(3));
        Assertions.assertEquals(1, referenceCounted.refCnt());
        Assertions.assertFalse(referenceCounted.isDeallocated());

        Assertions.assertTrue(referenceCounted.release());
        Assertions.assertEquals(0, referenceCounted.refCnt());
        Assertions.assertTrue(referenceCounted.isDeallocated());
    }

    private static final class CountingReferenceCounted extends AbstractReferenceCounted {
        private boolean deallocated;

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }

        @Override
        protected void deallocate() {
            deallocated = true;
        }

        private boolean isDeallocated() {
            return deallocated;
        }
    }
}
