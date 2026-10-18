/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.apache.pulsar.shade.org.apache.commons.lang3.AnnotationUtils;
import org.junit.jupiter.api.Test;

public class AnnotationUtilsTest {

    @Test
    void comparesHashesAndFormatsRuntimeAnnotations() {
        final Descriptor first = First.class.getAnnotation(Descriptor.class);
        final Descriptor second = Second.class.getAnnotation(Descriptor.class);
        final Descriptor different = Different.class.getAnnotation(Descriptor.class);

        assertThat(AnnotationUtils.equals(first, second)).isTrue();
        assertThat(AnnotationUtils.equals(first, different)).isFalse();
        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(AnnotationUtils.hashCode(second));
        assertThat(AnnotationUtils.toString(first)).contains("name=client", "ports", "6650", "6651");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Descriptor {
        String name();

        int[] ports();
    }

    @Descriptor(name = "client", ports = {6650, 6651})
    public static final class First {
    }

    @Descriptor(name = "client", ports = {6650, 6651})
    public static final class Second {
    }

    @Descriptor(name = "broker", ports = {8080})
    public static final class Different {
    }
}
