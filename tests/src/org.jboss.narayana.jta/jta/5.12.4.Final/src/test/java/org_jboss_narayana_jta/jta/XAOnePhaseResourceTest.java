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

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;
import com.arjuna.ats.arjuna.state.InputObjectState;
import com.arjuna.ats.arjuna.state.OutputObjectState;
import com.arjuna.ats.internal.jta.resources.arjunacore.XAOnePhaseResource;
import com.arjuna.ats.jta.xa.XidImple;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XAOnePhaseResourceTest {
    @Test
    void packAndUnpackSerializableXaResource() throws Exception {
        SerializableXaResource.COMMIT_CALLS.set(0);

        XAOnePhaseResource resource = new XAOnePhaseResource(
            new SerializableXaResource("pack-unpack"),
            new XidImple(new Uid()),
            null
        );
        OutputObjectState outputState = new OutputObjectState();

        resource.pack(outputState);

        XAOnePhaseResource restoredResource = new XAOnePhaseResource();
        restoredResource.unpack(new InputObjectState(outputState));

        assertThat(restoredResource.toString()).contains("pack-unpack");
        assertThat(restoredResource.commit()).isEqualTo(TwoPhaseOutcome.FINISH_OK);
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
}
