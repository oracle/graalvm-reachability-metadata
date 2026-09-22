/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_security.micronaut_security_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.module.info.MavenCoordinates;
import io.micronaut.security.info.MicronautSecurityAnnotationsModuleInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration coverage for Micronaut's module diagnostics service. */
@Timeout(55)
public class MicronautSecurityAnnotationsModuleInfoTest {
    @Test
    void exposesSecurityAnnotationsModuleDiagnostics() {
        MicronautSecurityAnnotationsModuleInfo module = new MicronautSecurityAnnotationsModuleInfo();
        MavenCoordinates coordinates = module.getMavenCoordinates().orElseThrow();

        assertThat(module.getId()).isEqualTo("io.micronaut.security:micronaut-security-annotations");
        assertThat(module.getName()).isEqualTo("micronaut-security-annotations");
        assertThat(module.getDescription()).contains("Official Security Solution for Micronaut");
        assertThat(module.getVersion()).isNotBlank();
        assertThat(coordinates.groupId()).isEqualTo("io.micronaut.security");
        assertThat(coordinates.artifactId()).isEqualTo("micronaut-security-annotations");
        assertThat(coordinates.version()).isEqualTo(module.getVersion());
        assertThat(module.getParentModuleId()).contains("io.micronaut.security:micronaut-security");
        assertThat(module.getTags()).isEmpty();
    }
}
