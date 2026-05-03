/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.bootstrap.runner.AotQuarkusEntryPoint;
import io.quarkus.bootstrap.runner.QuarkusEntryPoint;
import io.quarkus.bootstrap.runner.VirtualThreadSupport;
import org.junit.jupiter.api.Test;

public class EntryPointApiTest {
    @Test
    void entryPointTypesExposeApplicationDataLocationsAndConstructors() {
        QuarkusEntryPoint standardEntryPoint = new QuarkusEntryPoint();
        AotQuarkusEntryPoint aotEntryPoint = new AotQuarkusEntryPoint();
        VirtualThreadSupport virtualThreadSupport = new VirtualThreadSupport();

        assertThat(standardEntryPoint).isNotNull();
        assertThat(aotEntryPoint).isNotNull();
        assertThat(virtualThreadSupport).isNotNull();
        assertThat(QuarkusEntryPoint.QUARKUS_APPLICATION_DAT).isEqualTo("quarkus/quarkus-application.dat");
        assertThat(QuarkusEntryPoint.LIB_DEPLOYMENT_APPMODEL_DAT).isEqualTo("lib/deployment/appmodel.dat");
        assertThat(QuarkusEntryPoint.LIB_DEPLOYMENT_DEPLOYMENT_CLASS_PATH_DAT)
                .isEqualTo("lib/deployment/deployment-class-path.dat");
        assertThat(AotQuarkusEntryPoint.QUARKUS_APPLICATION_DAT).isEqualTo("quarkus/quarkus-application.dat");
    }
}
