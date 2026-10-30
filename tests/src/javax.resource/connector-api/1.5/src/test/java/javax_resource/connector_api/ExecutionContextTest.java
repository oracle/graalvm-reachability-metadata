/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_resource.connector_api;

import javax.resource.NotSupportedException;
import javax.resource.spi.work.ExecutionContext;
import javax.resource.spi.work.WorkManager;
import javax.transaction.xa.Xid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExecutionContextTest {
    @Test
    void carriesAnImportedTransaction() {
        ExecutionContext context = new ExecutionContext();
        Xid transaction = new Xid() {
            @Override
            public int getFormatId() {
                return 42;
            }

            @Override
            public byte[] getGlobalTransactionId() {
                return new byte[]{1, 2, 3};
            }

            @Override
            public byte[] getBranchQualifier() {
                return new byte[]{4, 5};
            }
        };

        assertNull(context.getXid());
        context.setXid(transaction);
        assertSame(transaction, context.getXid());
        assertEquals(42, context.getXid().getFormatId());
        assertArrayEquals(new byte[]{1, 2, 3}, context.getXid().getGlobalTransactionId());
        assertArrayEquals(new byte[]{4, 5}, context.getXid().getBranchQualifier());
        context.setXid(null);
        assertNull(context.getXid());
    }

    @Test
    void configuresTheImportedTransactionTimeout() throws NotSupportedException {
        ExecutionContext context = new ExecutionContext();

        assertEquals(WorkManager.UNKNOWN, context.getTransactionTimeout());
        context.setTransactionTimeout(30);
        assertEquals(30, context.getTransactionTimeout());
        context.setTransactionTimeout(60);
        assertEquals(60, context.getTransactionTimeout());
    }

    @Test
    void rejectsNonPositiveTransactionTimeouts() {
        ExecutionContext context = new ExecutionContext();

        assertThrows(NotSupportedException.class, () -> context.setTransactionTimeout(0));
        assertThrows(NotSupportedException.class, () -> context.setTransactionTimeout(-10));
        assertEquals(WorkManager.UNKNOWN, context.getTransactionTimeout());
    }
}
