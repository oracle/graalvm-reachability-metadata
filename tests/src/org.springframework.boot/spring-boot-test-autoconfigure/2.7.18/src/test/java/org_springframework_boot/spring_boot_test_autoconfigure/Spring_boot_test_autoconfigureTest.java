/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_test_autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.properties.AnnotationsPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_test_autoconfigureTest {
    @Test
    void comparesMappedPropertiesIndependentlyOfTheSourceName() {
        AnnotationsPropertySource first = new AnnotationsPropertySource(
                "first", AnnotationsPropertySourceTest.DefaultClient.class);
        AnnotationsPropertySource second = new AnnotationsPropertySource(
                "second", AnnotationsPropertySourceTest.DefaultClient.class);
        AnnotationsPropertySource customized = new AnnotationsPropertySource(
                AnnotationsPropertySourceTest.CustomClient.class);

        assertThat(first.getProperty("sample.client.display-name")).isEqualTo("inventory");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(customized);
    }
}
