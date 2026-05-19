/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_web3j.core;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.ens.contracts.generated.ENS;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthGetCode;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.websocket.events.Notification;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;
import org.web3j.tx.response.TransactionReceiptProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class ContractTest {
    static final String BINARY = "0x60006000";
    private static final String ENCODED_CONSTRUCTOR = "00";
    static final String DEPLOYED_ADDRESS = "0x000000000000000000000000000000000000dEaD";
    static final String TRANSACTION_HASH = "0x1";
    private static final String PRIVATE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000001";

    @Test
    void deployRemoteCallWithCredentialsCreatesContractWrapper() throws Exception {
        StubWeb3jService service = new StubWeb3jService();
        Web3j web3j = Web3j.build(service);
        try {
            Credentials credentials = Credentials.create(PRIVATE_KEY);
            ContractGasProvider gasProvider =
                    new StaticGasProvider(BigInteger.TEN, BigInteger.valueOf(21_000));

            ENS contract =
                    Contract.deployRemoteCall(
                                    ENS.class,
                                    web3j,
                                    credentials,
                                    gasProvider,
                                    BINARY,
                                    ENCODED_CONSTRUCTOR,
                                    BigInteger.valueOf(7))
                            .send();

            assertThat(contract.getContractAddress()).isEqualTo(DEPLOYED_ADDRESS);
            assertThat(contract.getTransactionReceipt()).isPresent();
            assertThat(service.sentRawTransactions).isEqualTo(1);
            assertThat(service.lastRawTransaction).isNotBlank();
        } finally {
            web3j.shutdown();
        }
        assertThat(service.closed).isTrue();
    }

    @Test
    void deployRemoteCallWithTransactionManagerCreatesContractWrapper() throws Exception {
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        ContractGasProvider gasProvider =
                new StaticGasProvider(BigInteger.ONE, BigInteger.valueOf(30_000));

        ENS contract =
                Contract.deployRemoteCall(
                                ENS.class,
                                null,
                                transactionManager,
                                gasProvider,
                                BINARY,
                                ENCODED_CONSTRUCTOR,
                                BigInteger.valueOf(11))
                        .send();

        assertThat(contract.getContractAddress()).isEqualTo(DEPLOYED_ADDRESS);
        assertThat(contract.getTransactionReceipt()).isPresent();
        assertThat(transactionManager.constructorTransaction).isTrue();
        assertThat(transactionManager.to).isNull();
        assertThat(transactionManager.data).isEqualTo(BINARY + ENCODED_CONSTRUCTOR);
        assertThat(transactionManager.value).isEqualTo(BigInteger.valueOf(11));
    }

    static TransactionReceipt successfulReceipt(String transactionHash) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(transactionHash);
        receipt.setStatus("0x1");
        receipt.setContractAddress(DEPLOYED_ADDRESS);
        return receipt;
    }
}

class RecordingTransactionManager extends TransactionManager {
    String to;
    String data;
    BigInteger value;
    boolean constructorTransaction;

    RecordingTransactionManager() {
        super(new ImmediateReceiptProcessor(), "0x0000000000000000000000000000000000000001");
    }

    @Override
    public EthSendTransaction sendTransaction(
            BigInteger gasPrice,
            BigInteger gasLimit,
            String to,
            String data,
            BigInteger value,
            boolean constructor) {
        this.to = to;
        this.data = data;
        this.value = value;
        this.constructorTransaction = constructor;
        EthSendTransaction transaction = new EthSendTransaction();
        transaction.setResult(ContractTest.TRANSACTION_HASH);
        return transaction;
    }

    @Override
    public EthSendTransaction sendEIP1559Transaction(
            long chainId,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas,
            BigInteger gasLimit,
            String to,
            String data,
            BigInteger value,
            boolean constructor) {
        return sendTransaction(maxPriorityFeePerGas, gasLimit, to, data, value, constructor);
    }

    @Override
    public String sendCall(String to, String data, DefaultBlockParameter defaultBlockParameter) {
        return "0x";
    }

    @Override
    public EthGetCode getCode(String contractAddress, DefaultBlockParameter defaultBlockParameter) {
        EthGetCode code = new EthGetCode();
        code.setResult(ContractTest.BINARY);
        return code;
    }
}

class ImmediateReceiptProcessor extends TransactionReceiptProcessor {
    ImmediateReceiptProcessor() {
        super(null);
    }

    @Override
    public TransactionReceipt waitForTransactionReceipt(String transactionHash)
            throws IOException, TransactionException {
        return ContractTest.successfulReceipt(transactionHash);
    }
}

class StubWeb3jService implements Web3jService {
    int sentRawTransactions;
    String lastRawTransaction;
    boolean closed;

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
        if (EthGetTransactionCount.class.equals(responseType)) {
            EthGetTransactionCount count = new EthGetTransactionCount();
            count.setResult("0x0");
            return responseType.cast(count);
        }
        if (EthSendTransaction.class.equals(responseType)) {
            lastRawTransaction = (String) request.getParams().get(0);
            sentRawTransactions++;
            EthSendTransaction transaction = new EthSendTransaction();
            transaction.setResult(Hash.sha3(lastRawTransaction));
            return responseType.cast(transaction);
        }
        if (EthGetTransactionReceipt.class.equals(responseType)) {
            String transactionHash = (String) request.getParams().get(0);
            EthGetTransactionReceipt response = new EthGetTransactionReceipt();
            response.setResult(ContractTest.successfulReceipt(transactionHash));
            return responseType.cast(response);
        }
        throw new IOException("Unexpected response type: " + responseType.getName());
    }

    @Override
    public <T extends Response> CompletableFuture<T> sendAsync(
            Request request, Class<T> responseType) {
        try {
            return CompletableFuture.completedFuture(send(request, responseType));
        } catch (IOException e) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Override
    public BatchResponse sendBatch(BatchRequest batchRequest) {
        throw new UnsupportedOperationException("Batch requests are not used by this test");
    }

    @Override
    public CompletableFuture<BatchResponse> sendBatchAsync(BatchRequest batchRequest) {
        CompletableFuture<BatchResponse> future = new CompletableFuture<>();
        future.completeExceptionally(
                new UnsupportedOperationException("Batch requests are not used by this test"));
        return future;
    }

    @Override
    public <T extends Notification<?>> Flowable<T> subscribe(
            Request request, String unsubscribeMethod, Class<T> responseType) {
        return Flowable.error(
                new UnsupportedOperationException("Subscriptions are not used by this test"));
    }

    @Override
    public void close() {
        closed = true;
    }
}

