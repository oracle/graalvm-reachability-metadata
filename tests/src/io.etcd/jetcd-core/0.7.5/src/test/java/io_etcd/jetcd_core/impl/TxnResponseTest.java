/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.api.DeleteRangeResponse;
import io.etcd.jetcd.api.PutResponse;
import io.etcd.jetcd.api.RangeResponse;
import io.etcd.jetcd.api.ResponseOp;
import io.etcd.jetcd.kv.TxnResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// `@org.junit.jupiter.api.Timeout(value = 30)` can't be used in the nativeTest GraalVM CE 22.3
public class TxnResponseTest {
    private TxnResponse txnResponse;

    @BeforeEach
    public void setUp() {
        io.etcd.jetcd.api.TxnResponse response = io.etcd.jetcd.api.TxnResponse.newBuilder()
                .addResponses(ResponseOp.newBuilder().setResponsePut(PutResponse.getDefaultInstance()))
                .addResponses(ResponseOp.newBuilder().setResponseDeleteRange(DeleteRangeResponse.getDefaultInstance()))
                .addResponses(ResponseOp.newBuilder().setResponseRange(RangeResponse.getDefaultInstance()))
                .addResponses(ResponseOp.newBuilder().setResponseTxn(io.etcd.jetcd.api.TxnResponse.getDefaultInstance()))
                .build();
        txnResponse = new TxnResponse(response, ByteSequence.EMPTY);
    }

    @Test
    public void getDeleteResponsesTest() {
        assertThat(txnResponse.getDeleteResponses().size()).isEqualTo(1);
    }

    @Test
    public void getPutResponsesTest() {
        assertThat(txnResponse.getPutResponses().size()).isEqualTo(1);
    }

    @Test
    public void getGetResponsesTest() {
        assertThat(txnResponse.getGetResponses().size()).isEqualTo(1);
    }

    @Test
    public void getTxnResponsesTest() {
        assertThat(txnResponse.getTxnResponses().size()).isEqualTo(1);
    }

}
