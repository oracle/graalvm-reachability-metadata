/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric_chaincode_java.fabric_chaincode_shim;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.fabric.contract.execution.JSONTransactionSerializer;
import org.hyperledger.fabric.contract.metadata.TypeSchema;
import org.hyperledger.fabric.shim.Chaincode.Response;
import org.hyperledger.fabric.shim.Chaincode.Response.Status;
import org.hyperledger.fabric.shim.ResponseUtils;
import org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement;
import org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement.RoleType;
import org.hyperledger.fabric.shim.ext.sbe.impl.StateBasedEndorsementFactory;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.Test;

public class FabricChaincodeShimTest {
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
}
