/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.sundr.builder.annotations.Buildable;
import org.junit.jupiter.api.Test;

public class Builder_annotationsTest {
    @Test
    void generatedBuilderSupportsLazyCollectionFluentMethods() {
        BuildableCatalog catalog = new BuildableCatalogBuilder()
                .withName("metadata-tests")
                .addToTags("builder", "native-image")
                .removeFromTags("native-image")
                .build();

        assertThat(catalog.getName()).isEqualTo("metadata-tests");
        assertThat(catalog.getTags()).containsExactly("builder");
    }

    @Test
    void generatedEditableTypeCanCreateBuilderFromExistingInstance() {
        EditableBuildableCatalog original = new BuildableCatalogBuilder()
                .withName("draft")
                .withTags("builder", "native-image")
                .build();

        BuildableCatalog updated = original.edit()
                .withName("published")
                .withTags("builder", "metadata")
                .build();

        assertThat(original.getName()).isEqualTo("draft");
        assertThat(original.getTags()).containsExactly("builder", "native-image");
        assertThat(updated.getName()).isEqualTo("published");
        assertThat(updated.getTags()).containsExactly("builder", "metadata");
    }
}

@Buildable
class BuildableCatalog {
    private final String name;
    private final List<String> tags;

    public BuildableCatalog(String name, List<String> tags) {
        this.name = name;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public List<String> getTags() {
        return tags;
    }
}
