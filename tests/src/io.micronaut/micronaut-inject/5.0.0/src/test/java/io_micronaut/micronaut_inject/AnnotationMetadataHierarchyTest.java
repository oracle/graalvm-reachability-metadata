/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotationMetadataHierarchyTest {
    @Test
    void synthesizesRepeatedAnnotationsAcrossMetadataHierarchy() {
        MutableAnnotationMetadata parent = metadataWith("parent");
        MutableAnnotationMetadata child = metadataWith("child");
        child.addDeclaredRepeatable(
                HierarchyTag.class.getName(),
                AnnotationValue.builder(HierarchyTag.class).member("value", "declared").build());

        AnnotationMetadataHierarchy hierarchy =
                new AnnotationMetadataHierarchy(true, parent, child);

        assertThat(hierarchy.synthesizeAnnotationsByType(HierarchyTag.class))
                .extracting(HierarchyTag::value)
                .containsExactlyInAnyOrder("parent", "child", "declared");
        assertThat(hierarchy.synthesizeDeclaredAnnotationsByType(HierarchyTag.class))
                .extracting(HierarchyTag::value)
                .containsExactlyInAnyOrder("child", "declared", "parent");
    }

    private static MutableAnnotationMetadata metadataWith(String value) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addRepeatable(
                HierarchyTag.class.getName(),
                AnnotationValue.builder(HierarchyTag.class).member("value", value).build());
        return metadata;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(HierarchyTags.class)
    public @interface HierarchyTag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface HierarchyTags {
        HierarchyTag[] value();
    }
}
