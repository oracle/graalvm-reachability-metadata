/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_service.helidon_service_metadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.metadata.hson.Hson;
import io.helidon.service.metadata.DescriptorMetadata;
import io.helidon.service.metadata.Descriptors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class Helidon_service_metadataTest {
    @Test
    void createsDescriptorMetadataAndWritesHson() {
        TypeName descriptorType = TypeName.create("example.InventoryDescriptor");
        Set<ResolvedType> contracts = Set.of(
                ResolvedType.create("example.service.ZetaService"),
                ResolvedType.create("example.service.AlphaService"));
        Set<ResolvedType> factoryContracts = Set.of(
                ResolvedType.create("example.factory.InventoryFactory"));

        DescriptorMetadata metadata = DescriptorMetadata.create(descriptorType, 42.5, contracts, factoryContracts);

        assertThat(metadata.descriptorType()).isEqualTo(descriptorType);
        assertThat(metadata.weight()).isEqualTo(42.5);
        assertThat(metadata.contracts()).containsExactlyInAnyOrderElementsOf(contracts);
        assertThat(metadata.factoryContracts()).containsExactlyInAnyOrderElementsOf(factoryContracts);

        Hson.Struct hson = metadata.toHson();
        assertThat(hson.stringValue("descriptor")).contains("example.InventoryDescriptor");
        assertThat(hson.doubleValue("weight")).contains(42.5);
        assertThat(hson.stringArray("contracts"))
                .contains(List.of("example.service.AlphaService", "example.service.ZetaService"));
        assertThat(hson.stringArray("factoryContracts"))
                .contains(List.of("example.factory.InventoryFactory"));
    }

    @Test
    void omitsDefaultDescriptorValuesFromHson() {
        DescriptorMetadata metadata = DescriptorMetadata.create(
                TypeName.create("example.DefaultDescriptor"),
                100.0,
                Set.of(ResolvedType.create("example.DefaultContract")),
                Set.of());

        Hson.Struct hson = metadata.toHson();

        assertThat(hson.keys()).contains("descriptor", "contracts");
        assertThat(hson.keys()).doesNotContain("weight", "factoryContracts");
        assertThat(hson.stringValue("descriptor")).contains("example.DefaultDescriptor");
        assertThat(hson.stringArray("contracts")).contains(List.of("example.DefaultContract"));
    }

    @Test
    void parsesDescriptorRegistryWithExplicitAndDefaultValues() {
        Hson.Struct weightedService = Hson.structBuilder()
                .set("version", 1)
                .set("descriptor", "example.InventoryDescriptor")
                .set("weight", 5.25)
                .setStrings("contracts", List.of("example.InventoryService", "example.NamedService"))
                .setStrings("factoryContracts", List.of("example.InventoryFactory"))
                .build();
        Hson.Struct defaultedService = Hson.structBuilder()
                .set("descriptor", "example.AuditDescriptor")
                .build();
        Hson.Struct registryModule = Hson.structBuilder()
                .set("module", "example.module")
                .set("version", 1)
                .setStructs("services", List.of(weightedService, defaultedService))
                .build();

        List<DescriptorMetadata> metadata = Descriptors.descriptors(
                "memory:services",
                Hson.Array.create(List.of(registryModule)));

        assertThat(metadata).hasSize(2);

        DescriptorMetadata weighted = metadata.get(0);
        assertThat(weighted.descriptorType().fqName()).isEqualTo("example.InventoryDescriptor");
        assertThat(weighted.weight()).isEqualTo(5.25);
        assertThat(weighted.contracts())
                .extracting(ResolvedType::resolvedName)
                .containsExactlyInAnyOrder("example.InventoryService", "example.NamedService");
        assertThat(weighted.factoryContracts())
                .extracting(ResolvedType::resolvedName)
                .containsExactly("example.InventoryFactory");

        DescriptorMetadata defaulted = metadata.get(1);
        assertThat(defaulted.descriptorType().fqName()).isEqualTo("example.AuditDescriptor");
        assertThat(defaulted.weight()).isEqualTo(100.0);
        assertThat(defaulted.contracts()).isEmpty();
        assertThat(defaulted.factoryContracts()).isEmpty();
    }

    @Test
    void writesAndParsesDescriptorRegistryHson() {
        Hson.Struct service = DescriptorMetadata.create(
                        TypeName.create("example.RoundTripDescriptor"),
                        12.0,
                        Set.of(ResolvedType.create("example.RoundTripContract")),
                        Set.of(ResolvedType.create("example.RoundTripFactory")))
                .toHson();
        Hson.Struct registryModule = Hson.structBuilder()
                .set("module", "round.trip.module")
                .setStructs("services", List.of(service))
                .build();
        Hson.Array registry = Hson.Array.create(List.of(registryModule));

        Hson.Array parsedRegistry = writeAndParse(registry).asArray();
        List<DescriptorMetadata> metadata = Descriptors.descriptors(
                Descriptors.SERVICE_REGISTRY_LOCATION,
                parsedRegistry);

        assertThat(Descriptors.SERVICE_REGISTRY_LOCATION).isEqualTo("META-INF/helidon/service-registry.json");
        assertThat(metadata).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.descriptorType().fqName()).isEqualTo("example.RoundTripDescriptor");
            assertThat(descriptor.weight()).isEqualTo(12.0);
            assertThat(descriptor.contracts())
                    .extracting(ResolvedType::resolvedName)
                    .containsExactly("example.RoundTripContract");
            assertThat(descriptor.factoryContracts())
                    .extracting(ResolvedType::resolvedName)
                    .containsExactly("example.RoundTripFactory");
        });
    }

    @Test
    void rejectsUnsupportedRegistryVersion() {
        Hson.Struct registryModule = Hson.structBuilder()
                .set("module", "future.module")
                .set("version", 2)
                .setStructs("services", List.of())
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> Descriptors.descriptors("memory:future", Hson.Array.create(List.of(registryModule))))
                .withMessageContaining("Invalid registry version")
                .withMessageContaining("future.module")
                .withMessageContaining("memory:future");
    }

    @Test
    void rejectsUnsupportedDescriptorVersion() {
        Hson.Struct service = Hson.structBuilder()
                .set("version", 2)
                .set("descriptor", "example.FutureDescriptor")
                .build();
        Hson.Struct registryModule = Hson.structBuilder()
                .set("module", "future.descriptor.module")
                .setStructs("services", List.of(service))
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> Descriptors.descriptors(
                        "memory:future-descriptor",
                        Hson.Array.create(List.of(registryModule))))
                .withMessageContaining("Invalid descriptor version")
                .withMessageContaining("future.descriptor.module")
                .withMessageContaining("memory:future-descriptor")
                .withMessageContaining("example.FutureDescriptor");
    }

    @Test
    void requiresServiceDescriptorType() {
        Hson.Struct service = Hson.structBuilder()
                .setStrings("contracts", List.of("example.ContractWithoutDescriptor"))
                .build();
        Hson.Struct registryModule = Hson.structBuilder()
                .set("module", "missing.descriptor.module")
                .setStructs("services", List.of(service))
                .build();

        assertThatIllegalStateException()
                .isThrownBy(() -> Descriptors.descriptors(
                        "memory:missing-descriptor",
                        Hson.Array.create(List.of(registryModule))))
                .withMessageContaining("Could not parse service metadata")
                .withMessageContaining("missing.descriptor.module")
                .withMessageContaining("memory:missing-descriptor");
    }

    private static Hson.Value<?> writeAndParse(Hson.Value<?> value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8)) {
            value.write(writer);
        }
        byte[] bytes = output.toByteArray();
        return Hson.parse(new ByteArrayInputStream(bytes));
    }
}
