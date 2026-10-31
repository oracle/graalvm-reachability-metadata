/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ReferenceCountUpdater;
import io.netty.util.internal.UnsafeReferenceCountUpdater;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ReferenceCountUpdaterTest {
    @Test
    void updatesAUserDefinedReferenceCountField() {
        CountedResource resource = new CountedResource();
        Assertions.assertTrue(CountedResource.COUNT_OFFSET >= 0);
        Assertions.assertEquals(1, resource.refCnt());
        Assertions.assertSame(resource, resource.retain(3));
        Assertions.assertEquals(4, resource.refCnt());
        Assertions.assertFalse(resource.release(2));
        Assertions.assertEquals(2, resource.refCnt());
        Assertions.assertFalse(resource.release());
        Assertions.assertTrue(resource.release());
        Assertions.assertEquals(0, resource.refCnt());
    }

    public static final class CountedResource implements ReferenceCounted {
        private static final long COUNT_OFFSET = ReferenceCountUpdater.getUnsafeOffset(CountedResource.class, "rawCount");
        private static final ReferenceCountUpdater<CountedResource> UPDATER = new UnsafeReferenceCountUpdater<>() {
            @Override
            protected long refCntFieldOffset() {
                return COUNT_OFFSET;
            }
        };

        private volatile int rawCount;

        public CountedResource() {
            UPDATER.setInitialValue(this);
        }

        @Override
        public int refCnt() {
            return UPDATER.refCnt(this);
        }

        @Override
        public ReferenceCounted retain() {
            return UPDATER.retain(this);
        }

        @Override
        public ReferenceCounted retain(int increment) {
            return UPDATER.retain(this, increment);
        }

        @Override
        public ReferenceCounted touch() {
            return this;
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }

        @Override
        public boolean release() {
            return UPDATER.release(this);
        }

        @Override
        public boolean release(int decrement) {
            return UPDATER.release(this, decrement);
        }
    }
}
