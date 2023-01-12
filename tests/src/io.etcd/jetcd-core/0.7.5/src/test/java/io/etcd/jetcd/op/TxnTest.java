/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.etcd.jetcd.op;

import io.etcd.jetcd.options.PutOption;
import org.junit.jupiter.api.Test;

import static io_etcd.jetcd_core.impl.TestUtil.bytesOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TxnTest {
    private static final Cmp CMP = new Cmp(bytesOf("key"), Cmp.Op.GREATER, CmpTarget.value(bytesOf("value")));
    private static final Op OP = Op.put(bytesOf("key2"), bytesOf("value2"), PutOption.DEFAULT);

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testIfs() {
        TxnImpl.newTxn((t) -> null).If(CMP).If(CMP).commit();
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testThens() {
        TxnImpl.newTxn((t) -> null).Then(OP).Then(OP).commit();
    }

    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testElses() {
        TxnImpl.newTxn((t) -> null).Else(OP).Else(OP).commit();
    }

    @Test
    public void testIfAfterThen() {
        assertThatThrownBy(() -> TxnImpl.newTxn((t) -> null).Then(OP).If(CMP).commit().get())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot call If after Then!");
    }

    @Test
    public void testIfAfterElse() {
        assertThatThrownBy(() -> TxnImpl.newTxn((t) -> null).Else(OP).If(CMP).commit().get())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot call If after Else!");
    }

    @Test
    public void testThenAfterElse() {
        assertThatThrownBy(() -> TxnImpl.newTxn((t) -> null).Else(OP).Then(OP).commit().get())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot call Then after Else!");
    }
}
