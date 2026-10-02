/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hyperledger_fabric_chaincode_java.fabric_chaincode_shim;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.metadata.MetadataBuilder;
import org.hyperledger.fabric.contract.routing.ContractDefinition;
import org.hyperledger.fabric.contract.routing.RoutingRegistry;
import org.hyperledger.fabric.contract.routing.impl.RoutingRegistryImpl;
import org.hyperledger.fabric.contract.systemcontract.SystemContract;
import org.junit.jupiter.api.Test;

public class MetadataBuilderTest {
    @Test
    @SuppressWarnings("unchecked")
    void buildsMetadataFromAnAnnotatedSystemContract() {
        RoutingRegistry registry = new RoutingRegistryImpl();
        Class<ContractInterface> contractClass = (Class<ContractInterface>) (Class<?>) SystemContract.class;
        ContractDefinition definition = registry.addNewContract(contractClass);

        String contractName = MetadataBuilder.addContract(definition);
        String metadata = MetadataBuilder.getMetadata();

        assertThat(contractName).isEqualTo("org.hyperledger.fabric");
        assertThat(metadata)
                .contains("\"name\":\"org.hyperledger.fabric\"")
                .contains("\"title\":\"Fabric System Contract\"")
                .contains("Provides information about the contracts within this container");
    }
}
