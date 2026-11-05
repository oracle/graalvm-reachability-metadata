/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RefCntInnerUnsafeRefCntTest {
    @Test
    void deallocatesResourceOnlyAfterLastReferenceIsReleased() {
        CountingResource resource = new CountingResource();
        Assertions.assertEquals(1, resource.refCnt());
        Assertions.assertSame(resource, resource.retain());
        Assertions.assertSame(resource, resource.retain(2));
        Assertions.assertEquals(4, resource.refCnt());
        Assertions.assertFalse(resource.release(3));
        Assertions.assertEquals(1, resource.refCnt());
        Assertions.assertEquals(0, resource.deallocations);
        Assertions.assertTrue(resource.release());
        Assertions.assertEquals(0, resource.refCnt());
        Assertions.assertEquals(1, resource.deallocations);
        Assertions.assertThrows(IllegalReferenceCountException.class, resource::release);
        Assertions.assertEquals(1, resource.deallocations);
    }

    private static final class CountingResource extends AbstractReferenceCounted {
        private int deallocations;

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }

        @Override
        protected void deallocate() {
            deallocations++;
        }
    }
}
