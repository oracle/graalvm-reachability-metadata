/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_api;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.glassfish.hk2.api.Metadata;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.junit.jupiter.api.Test;

public class BuilderHelperAnonymous3Test {
    @Test
    void getMetadataValuesReadsMetadataAnnotatedAnnotationMethods() {
        final Class<AnnotatedService> annotatedServiceAnnotationAccess = AnnotatedService.class;
        final ServiceMetadata annotation = annotatedServiceAnnotationAccess.getAnnotation(ServiceMetadata.class);
        final Map<String, List<String>> metadata = new HashMap<>();

        BuilderHelper.getMetadataValues(annotation, metadata);

        assertThat(metadata).containsOnlyKeys("name", "contract", "tags");
        assertThat(metadata.get("name")).containsExactly("configured-service");
        assertThat(metadata.get("contract")).containsExactly(Runnable.class.getName());
        assertThat(metadata.get("tags")).containsExactly("fast", "native-image");
    }

    @ServiceMetadata(
            name = "configured-service",
            contract = Runnable.class,
            tags = {"fast", "native-image"},
            ignored = "not metadata")
    private static final class AnnotatedService {
    }

    @Retention(RUNTIME)
    @Target(TYPE)
    private @interface ServiceMetadata {
        @Metadata("name")
        String name();

        @Metadata("contract")
        Class<?> contract();

        @Metadata("tags")
        String[] tags();

        String ignored();
    }
}
