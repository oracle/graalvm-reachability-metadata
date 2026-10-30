/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import io.sundr.builder.annotations.BuildableReference;
import io.sundr.builder.annotations.ExternalBuildables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ExternalBuildables(refs = @BuildableReference(ShippingLabel.class), editableEnabled = false)
public class ExternalBuildablesTest {
    @Test
    void buildsAnUnannotatedTypeFromAnExternalReference() {
        ShippingLabel label = new ShippingLabelBuilder()
                .withRecipient("Ada")
                .withCopies(2)
                .build();

        assertThat(label.getRecipient()).isEqualTo("Ada");
        assertThat(label.getCopies()).isEqualTo(2);
    }

    @Test
    void copiesAnUnannotatedTypeIntoABuilder() {
        ShippingLabel original = new ShippingLabel("Grace", 1);

        ShippingLabel copy = new ShippingLabelBuilder(original).withCopies(3).build();

        assertThat(copy.getRecipient()).isEqualTo("Grace");
        assertThat(copy.getCopies()).isEqualTo(3);
        assertThat(original.getCopies()).isEqualTo(1);
    }
}
