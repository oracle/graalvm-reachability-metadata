/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.internal.shaded.org.jctools.util.UnsafeAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnsafeAccessTest {
    @Test
    void resolvesUnsafeAndDeclaredFieldOffsets() {
        Assertions.assertNotNull(UnsafeAccess.UNSAFE);

        long valueOffset = UnsafeAccess.fieldOffset(OffsetHolder.class, "value");
        OffsetHolder holder = new OffsetHolder();
        Object marker = new Object();

        UnsafeAccess.UNSAFE.putObject(holder, valueOffset, marker);

        Assertions.assertSame(marker, holder.value);
    }

    private static final class OffsetHolder {
        private Object value;
    }
}
