/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_narayana_jta.jta;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import com.arjuna.ats.arjuna.ObjectType;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.jta.resources.arjunacore.XAResourceRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XAResourceRecordTest {
    @Test
    void saveAndRestoreStateWithSerializableXaResource() throws Exception {
        SerializableXaResource.COMMIT_CALLS.set(0);

        XAResourceRecord resourceRecord = new XAResourceRecord(
            null,
            new SerializableXaResource("xa-resource-record"),
            new SerializableXid(73, new byte[]{1, 3, 5}, new byte[]{2, 4, 6}),
            null
        );
        resourceRecord.setProductName("Test Product");
        resourceRecord.setProductVersion("2026.04");
        resourceRecord.setJndiName("java:/test/XAResourceRecord");

        OutputObjectState outputState = new OutputObjectState();

        assertThat(resourceRecord.save_state(outputState, ObjectType.ANDPERSISTENT)).isTrue();

        XAResourceRecord restoredRecord = new XAResourceRecord();

        assertThat(restoredRecord.restore_state(new InputObjectState(outputState), ObjectType.ANDPERSISTENT)).isTrue();
        assertThat(restoredRecord.getProductName()).isEqualTo("Test Product");
        assertThat(restoredRecord.getProductVersion()).isEqualTo("2026.04");
        assertThat(restoredRecord.getJndiName()).isEqualTo("java:/test/XAResourceRecord");
        assertThat(restoredRecord.getXid().getFormatId()).isEqualTo(73);
        assertThat(restoredRecord.getXid().getGlobalTransactionId()).containsExactly((byte) 1, (byte) 3, (byte) 5);
        assertThat(restoredRecord.getXid().getBranchQualifier()).containsExactly((byte) 2, (byte) 4, (byte) 6);
        assertThat(restoredRecord.toString()).contains("xa-resource-record");

        XAResource restoredResource = (XAResource) restoredRecord.value();

        assertThat(restoredResource).isInstanceOf(SerializableXaResource.class);

        restoredResource.commit(restoredRecord.getXid(), true);

        assertThat(SerializableXaResource.COMMIT_CALLS).hasValue(1);
    }

    private static final class SerializableXaResource implements XAResource, Serializable {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger COMMIT_CALLS = new AtomicInteger();

        private final String name;

        private SerializableXaResource(String name) {
            this.name = name;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            COMMIT_CALLS.incrementAndGet();
        }

        @Override
        public void end(Xid xid, int flags) {
        }

        @Override
        public void forget(Xid xid) {
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource == this;
        }

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void rollback(Xid xid) {
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return true;
        }

        @Override
        public void start(Xid xid, int flags) {
        }

        @Override
        public String toString() {
            return "SerializableXaResource[" + name + "]";
        }
    }

    private static final class SerializableXid implements Xid, Serializable {
        private static final long serialVersionUID = 1L;

        private final int formatId;
        private final byte[] globalTransactionId;
        private final byte[] branchQualifier;

        private SerializableXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
            this.formatId = formatId;
            this.globalTransactionId = globalTransactionId;
            this.branchQualifier = branchQualifier;
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalTransactionId;
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchQualifier;
        }
    }
}
