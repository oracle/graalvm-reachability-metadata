/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric.fabric_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.hyperledger.fabric.client.CloseableIterator;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.Status;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.protos.gateway.ChaincodeEventsResponse;
import org.hyperledger.fabric.protos.gateway.CommitStatusRequest;
import org.hyperledger.fabric.protos.gateway.CommitStatusResponse;
import org.hyperledger.fabric.protos.gateway.SignedCommitStatusRequest;
import org.hyperledger.fabric.protos.gateway.EvaluateResponse;
import org.hyperledger.fabric.protos.gateway.GatewayGrpc;
import org.hyperledger.fabric.protos.peer.ChaincodeEvent.Builder;
import org.hyperledger.fabric.protos.peer.Response;
import org.hyperledger.fabric.protos.peer.TxValidationCode;
import org.junit.jupiter.api.Test;

public class Fabric_gatewayTest {
    @Test
    void evaluatesTransactionsAndReadsCommitAndChaincodeEvents() throws Exception {
        String serverName = "fabric-gateway-test";
        AtomicInteger evaluateCalls = new AtomicInteger();
        GatewayGrpc.GatewayImplBase service = new GatewayGrpc.GatewayImplBase() {
            @Override
            public void evaluate(
                    org.hyperledger.fabric.protos.gateway.EvaluateRequest request,
                    StreamObserver<EvaluateResponse> observer) {
                evaluateCalls.incrementAndGet();
                observer.onNext(
                        EvaluateResponse.newBuilder()
                                .setResult(
                                        Response.newBuilder()
                                                .setStatus(200)
                                                .setPayload(ByteString.copyFromUtf8("evaluated"))
                                                .build())
                                .build());
                observer.onCompleted();
            }

            @Override
            public void commitStatus(
                    org.hyperledger.fabric.protos.gateway.SignedCommitStatusRequest request,
                    StreamObserver<CommitStatusResponse> observer) {
                observer.onNext(
                        CommitStatusResponse.newBuilder()
                                .setResult(TxValidationCode.VALID)
                                .setBlockNumber(42)
                                .build());
                observer.onCompleted();
            }

            @Override
            public void chaincodeEvents(
                    org.hyperledger.fabric.protos.gateway.SignedChaincodeEventsRequest request,
                    StreamObserver<ChaincodeEventsResponse> observer) {
                Builder event = org.hyperledger.fabric.protos.peer.ChaincodeEvent.newBuilder()
                        .setChaincodeId("ledger")
                        .setTxId("transaction-1")
                        .setEventName("updated")
                        .setPayload(ByteString.copyFromUtf8("event-payload"));
                observer.onNext(
                        ChaincodeEventsResponse.newBuilder()
                                .addEvents(event.build())
                                .setBlockNumber(42)
                                .build());
                observer.onCompleted();
            }
        };

        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        Identity identity = new Identity() {
            @Override
            public String getMspId() {
                return "TestMSP";
            }

            @Override
            public byte[] getCredentials() {
                return "test-certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        };
        Signer signer = (byte[] data) -> sign(data);

        try (Gateway gateway = Gateway.newInstance()
                .identity(identity)
                .signer(signer)
                .connection(channel)
                .connect()) {
            Network network = gateway.getNetwork("test-channel");
            Contract contract = network.getContract("ledger");

            assertThat(contract.evaluateTransaction("read", "asset-1"))
                    .containsExactly("evaluated".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(evaluateCalls).hasValue(1);

            byte[] signedCommitStatusRequest = SignedCommitStatusRequest.newBuilder()
                    .setRequest(CommitStatusRequest.newBuilder()
                            .setChannelId("test-channel")
                            .setTransactionId("transaction-1")
                            .setIdentity(ByteString.copyFromUtf8("test-certificate"))
                            .build()
                            .toByteString())
                    .setSignature(ByteString.copyFromUtf8("signature"))
                    .build()
                    .toByteArray();
            Status status = gateway.newCommit(signedCommitStatusRequest).getStatus();
            assertThat(status.isSuccessful()).isTrue();
            assertThat(status.getBlockNumber()).isEqualTo(42);
            assertThat(status.getCode()).isEqualTo(TxValidationCode.VALID);

            try (CloseableIterator<ChaincodeEvent> events = network.getChaincodeEvents("ledger")) {
                assertThat(events.hasNext()).isTrue();
                ChaincodeEvent event = events.next();
                assertThat(event.getBlockNumber()).isEqualTo(42);
                assertThat(event.getTransactionId()).isEqualTo("transaction-1");
                assertThat(event.getEventName()).isEqualTo("updated");
                assertThat(event.getPayload()).containsExactly(
                        "event-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(events.hasNext()).isFalse();
            }
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    private static byte[] sign(byte[] data) throws GeneralSecurityException {
        return data;
    }
}
