/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildableTest {
    @Test
    void buildsNestedObjectsCollectionsAndMaps() {
        Shipment shipment = new ShipmentBuilder()
                .withReference("parcel-a")
                .withNewDestination()
                    .withCity("Vienna")
                    .withPostalCode("1010")
                .endDestination()
                .addToItems("book", "notebook")
                .addToAttributes("service", "express")
                .build();

        assertThat(shipment.getReference()).isEqualTo("parcel-a");
        assertThat(shipment.getDestination().getCity()).isEqualTo("Vienna");
        assertThat(shipment.getDestination().getPostalCode()).isEqualTo("1010");
        assertThat(shipment.getItems()).containsExactly("book", "notebook");
        assertThat(shipment.getAttributes()).containsEntry("service", "express");
    }

    @Test
    void copiesAndEditsWithoutChangingTheOriginal() {
        Shipment original = new ShipmentBuilder()
                .withReference("parcel-b")
                .withNewDestination()
                    .withCity("Linz")
                    .withPostalCode("4020")
                .endDestination()
                .addToItems("book", "pencil")
                .addToAttributes("service", "standard")
                .build();

        Shipment copy = new ShipmentBuilder(original)
                .withReference("parcel-c")
                .editDestination().withCity("Graz").withPostalCode("8010").endDestination()
                .removeFromItems("pencil")
                .addToItems("pen")
                .addToAttributes("service", "express")
                .build();

        assertThat(copy.getReference()).isEqualTo("parcel-c");
        assertThat(copy.getDestination().getCity()).isEqualTo("Graz");
        assertThat(copy.getDestination().getPostalCode()).isEqualTo("8010");
        assertThat(copy.getItems()).containsExactly("book", "pen");
        assertThat(copy.getAttributes()).containsEntry("service", "express");
        assertThat(original.getReference()).isEqualTo("parcel-b");
        assertThat(original.getDestination().getCity()).isEqualTo("Linz");
        assertThat(original.getDestination().getPostalCode()).isEqualTo("4020");
        assertThat(original.getItems()).containsExactly("book", "pencil");
        assertThat(original.getAttributes()).containsEntry("service", "standard");
    }

    @Test
    void editsAnEditableBuildResult() {
        EditableAddress original = new AddressBuilder()
                .withCity("Salzburg")
                .withPostalCode("5020")
                .build();

        Address edited = original.edit().withPostalCode("5026").build();

        assertThat(edited.getCity()).isEqualTo("Salzburg");
        assertThat(edited.getPostalCode()).isEqualTo("5026");
        assertThat(original.getPostalCode()).isEqualTo("5020");
    }
}
