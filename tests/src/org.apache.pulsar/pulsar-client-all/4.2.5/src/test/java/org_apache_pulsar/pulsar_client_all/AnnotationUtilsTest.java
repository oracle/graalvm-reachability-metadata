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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class AnnotationUtilsTest {
    @Test
    @SuppressWarnings("checkstyle:annotationAccess")
    void comparesHashesAndFormatsAnnotationMembers() {
        // Checkstyle: allow direct annotation access
        Service first = FirstService.class.getAnnotation(Service.class);
        Service second = SecondService.class.getAnnotation(Service.class);
        // Checkstyle: disallow direct annotation access

        assertThat(AnnotationUtils.equals(first, second)).isTrue();
        assertThat(AnnotationUtils.hashCode(first)).isEqualTo(AnnotationUtils.hashCode(second));
        assertThat(AnnotationUtils.toString(first)).contains("name=pulsar", "ports");
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Service {
        String name();

        int[] ports();
    }

    @Service(name = "pulsar", ports = {6650, 8080})
    public static final class FirstService { }

    @Service(name = "pulsar", ports = {6650, 8080})
    public static final class SecondService { }
}
