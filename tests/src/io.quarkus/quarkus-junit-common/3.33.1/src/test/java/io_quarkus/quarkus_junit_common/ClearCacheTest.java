/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_junit_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import io.quarkus.test.junit.common.ClearCache;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.AnnotationUtils;

public class ClearCacheTest {

    @Test
    void clearCachesClearsJUnitRepeatableAnnotationCacheWithoutBreakingLookup() {
        List<Marker> annotationsBeforeClearing = AnnotationUtils.findRepeatableAnnotations(AnnotatedType.class, Marker.class);
        assertThat(annotationsBeforeClearing)
                .extracting(Marker::value)
                .containsExactlyInAnyOrder("alpha", "bravo");

        ClearCache.clearCaches();

        List<Marker> annotationsAfterClearing = AnnotationUtils.findRepeatableAnnotations(AnnotatedType.class, Marker.class);
        assertThat(annotationsAfterClearing)
                .extracting(Marker::value)
                .containsExactlyInAnyOrder("alpha", "bravo");
    }

    @Marker("alpha")
    @Marker("bravo")
    private static final class AnnotatedType {
    }

    @Repeatable(Markers.class)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface Marker {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Markers {
        Marker[] value();
    }
}
