/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric.fabric_protos;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.peer.ChaincodeEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ChaincodeEventTest {

    @Test
    void serializesAndMutatesEventThroughProtobufMessageApi() throws InvalidProtocolBufferException {
        Descriptor descriptor = ChaincodeEvent.getDescriptor();
        FieldDescriptor chaincodeIdField = descriptor.findFieldByName("chaincode_id");
        FieldDescriptor transactionIdField = descriptor.findFieldByName("tx_id");
        FieldDescriptor eventNameField = descriptor.findFieldByName("event_name");
        FieldDescriptor payloadField = descriptor.findFieldByName("payload");
        ByteString payload = ByteString.copyFromUtf8("asset-42");

        ChaincodeEvent event = ChaincodeEvent.newBuilder()
                .setField(chaincodeIdField, "asset-transfer")
                .setField(transactionIdField, "transaction-7")
                .setField(eventNameField, "created")
                .setField(payloadField, payload)
                .build();
        ChaincodeEvent parsed = ChaincodeEvent.parseFrom(event.toByteArray());
        Map<FieldDescriptor, Object> reflectedFields = parsed.getAllFields();

        assertThat(parsed.getChaincodeId()).isEqualTo("asset-transfer");
        assertThat(parsed.getTxId()).isEqualTo("transaction-7");
        assertThat(parsed.getEventName()).isEqualTo("created");
        assertThat(parsed.getPayload()).isEqualTo(payload);
        assertThat(reflectedFields)
                .containsEntry(chaincodeIdField, "asset-transfer")
                .containsEntry(transactionIdField, "transaction-7")
                .containsEntry(eventNameField, "created")
                .containsEntry(payloadField, payload);

        ChaincodeEvent updated = parsed.toBuilder()
                .setField(eventNameField, "transferred")
                .clearField(payloadField)
                .build();

        assertThat(updated.getEventName()).isEqualTo("transferred");
        assertThat(updated.getPayload()).isEqualTo(ByteString.EMPTY);
        assertThat(updated.getAllFields()).doesNotContainKey(payloadField);
    }
}
