/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric_chaincode_java.fabric_chaincode_shim;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.contract.execution.JSONTransactionSerializer;
import org.hyperledger.fabric.contract.metadata.TypeSchema;
import org.hyperledger.fabric.protos.msp.SerializedIdentity;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.Chaincode.Response.Status;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ResponseUtils;
import org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement;
import org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement.RoleType;
import org.hyperledger.fabric.shim.ext.sbe.impl.StateBasedEndorsementFactory;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.Test;

public class FabricChaincodeShimTest {
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDNzCCAh+gAwIBAgIUalIn3cIFHlehiGdD5fr8AcXI+AkwDQYJKoZIhvcNAQEL
            BQAwKjEZMBcGA1UEAwwQRmFicmljIFRlc3QgVXNlcjENMAsGA1UECgwET3JnMTAg
            Fw0yNjA5MjcxNTA5MzdaGA8yMTI2MDkwMzE1MDkzN1owKjEZMBcGA1UEAwwQRmFi
            cmljIFRlc3QgVXNlcjENMAsGA1UECgwET3JnMTCCASIwDQYJKoZIhvcNAQEBBQAD
            ggEPADCCAQoCggEBAL/BhI869LFo1N1QcPvyuBe3P5POepSgChS84UmlpwE9FFdU
            UbEokIZeXS/ejKVbRh0HN7Aj8o/2+TOPmuiyya2s8W1UHxG7/CMyFLZ98Fwv1471
            Zd5UGzjwcY4SFqf7qefh8QimW62MizB1TjcFpLmVPJN5WA9ujyj5q3qURL44eTpO
            A3M7vs4CQi5p3WP5i5PT+fQQgYBrwrZ2qT4CNm38xxQmYi/5cOx+s7JXCeTW8O+R
            Jzn8czVDE/6mnNSsQ25skXihI2ufncIhdQt2glP0ZeyBVkVUp1t6MWkhgZrkJZZJ
            7SnfEoD6RoI5OPxTIugxNnuwELtO1Ex47pBt9PkCAwEAAaNTMFEwHQYDVR0OBBYE
            FCpuo5VdFj5eYJXYEcBweFcMlX8XMB8GA1UdIwQYMBaAFCpuo5VdFj5eYJXYEcBw
            eFcMlX8XMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAJeBO/gz
            DKE+y8FI53iJVuh+z8J247V2r9sBKyJgiWlFxB3AXIdvNbIOQ7G/DbAV7XC9j5P9
            Q09BSg2hjnyKNo+g4QsNf9leY3DaWHJwpPVZl6rkSZrl38cRQ8IRsOnw4kjHR+VL
            TV8Ev6IZ0+s3l61TKkpcIF0LeiPdJZi8Vb9OZs4VunfRx6K6e6lNprYCILtFUo8+
            iEE4HwD+56lUqiAUKlYTDK2Y9t19Zzw6Gt5iEY1fRfsXqQxX/xpzw87PPiJ7aOyN
            us9M5F3nLZjfY+9FNNGEIyFgx6VGWJwJa3yh/271bSCzlIYkozZ3AaQg1VoTLjBr
            dHxQ+vNTiS2KFzo=
            -----END CERTIFICATE-----
            """;

    @Test
    void contractTransactionsReadAndUpdateLedgerState() {
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(stub.getCreator()).thenReturn(serializedIdentity());
        when(stub.getState("asset-1")).thenReturn(new byte[0], "Alice|41".getBytes(UTF_8));
        Context context = new Context(stub);
        AssetContract contract = new AssetContract();

        String created = contract.createAsset(context, "asset-1", "Alice", 41);
        String transferred = contract.transferAsset(context, "asset-1", "Bob");

        assertThat(created).isEqualTo("Alice|41");
        assertThat(transferred).isEqualTo("Bob|41");
        assertThat(context.getClientIdentity().getMSPID()).isEqualTo("Org1MSP");
        assertThat(context.getClientIdentity().getId()).contains("Fabric Test User");
        verify(stub).putState("asset-1", "Alice|41".getBytes(UTF_8));
        verify(stub).putState("asset-1", "Bob|41".getBytes(UTF_8));
    }

    @Test
    void transactionSerializerConvertsWireValuesUsingTypeSchemas() {
        JSONTransactionSerializer serializer = new JSONTransactionSerializer();
        TypeSchema namesSchema = TypeSchema.typeConvert(String[].class);
        TypeSchema amountSchema = TypeSchema.typeConvert(int.class);

        byte[] namesBuffer = serializer.toBuffer(new String[] {"asset-1", "asset-2"}, namesSchema);
        Object names = serializer.fromBuffer(namesBuffer, namesSchema);
        Object amount = serializer.fromBuffer("73".getBytes(UTF_8), amountSchema);

        assertThat((String[]) names).containsExactly("asset-1", "asset-2");
        assertThat(amount).isEqualTo(73);
    }

    @Test
    void stateBasedEndorsementPolicyRoundTripsAndCanBeChanged() {
        StateBasedEndorsementFactory factory = StateBasedEndorsementFactory.getInstance();
        StateBasedEndorsement policy = factory.newStateBasedEndorsement(new byte[0]);
        policy.addOrgs(RoleType.RoleTypePeer, "Org1MSP", "Org2MSP");

        byte[] encodedPolicy = policy.policy();
        StateBasedEndorsement restored = factory.newStateBasedEndorsement(encodedPolicy);
        restored.delOrgs("Org1MSP");

        assertThat(encodedPolicy).isNotEmpty();
        assertThat(policy.listOrgs()).containsExactlyInAnyOrder("Org1MSP", "Org2MSP");
        assertThat(restored.listOrgs()).containsExactly("Org2MSP");
        assertThat(restored.policy()).isNotEmpty().isNotEqualTo(encodedPolicy);
    }

    @Test
    void compositeKeysAndResponsesPreserveChaincodeValues() {
        CompositeKey key = new CompositeKey("asset", "owner", "asset-1");
        CompositeKey parsed = CompositeKey.parseCompositeKey(key.toString());
        Response response = ResponseUtils.newSuccessResponse("stored", key.toString().getBytes(UTF_8));

        assertThat(parsed.getObjectType()).isEqualTo("asset");
        assertThat(parsed.getAttributes()).containsExactly("owner", "asset-1");
        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getMessage()).isEqualTo("stored");
        assertThat(response.getStringPayload()).isEqualTo(key.toString());
    }

    private static byte[] serializedIdentity() {
        return SerializedIdentity.newBuilder()
                .setMspid("Org1MSP")
                .setIdBytes(ByteString.copyFrom(CERTIFICATE, UTF_8))
                .build()
                .toByteArray();
    }

    @Contract(name = "AssetContract")
    public static final class AssetContract implements ContractInterface {
        @Transaction(intent = Transaction.TYPE.SUBMIT)
        public String createAsset(
                final Context context, final String assetId, final String owner, final int appraisedValue) {
            ChaincodeStub stub = context.getStub();
            byte[] current = stub.getState(assetId);
            if (current != null && current.length > 0) {
                throw new IllegalArgumentException("Asset already exists");
            }
            String asset = owner + "|" + appraisedValue;
            stub.putState(assetId, asset.getBytes(UTF_8));
            return asset;
        }

        @Transaction(intent = Transaction.TYPE.SUBMIT)
        public String transferAsset(final Context context, final String assetId, final String newOwner) {
            ChaincodeStub stub = context.getStub();
            String current = new String(stub.getState(assetId), UTF_8);
            String updated = newOwner + current.substring(current.indexOf('|'));
            stub.putState(assetId, updated.getBytes(UTF_8));
            return updated;
        }
    }
}
