/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import io.sundr.builder.annotations.Buildable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Builder_annotationsTest {
    @Test
    void generatesBuilderWithEagerCollectionInitialization() {
        CollectionHolder emptyHolder = new CollectionHolderBuilder().build();
        CollectionHolder populatedHolder = new CollectionHolderBuilder()
            .addToLabels("stable")
            .addToLabels("native")
            .build();

        assertThat(emptyHolder.getLabels()).isEmpty();
        assertThat(populatedHolder.getLabels()).containsExactly("stable", "native");
    }
}

@Buildable(lazyCollectionInitEnabled = false)
class CollectionHolder {
    private final List<String> labels;

    CollectionHolder(List<String> labels) {
        this.labels = labels;
    }

    List<String> getLabels() {
        return labels;
    }
}
