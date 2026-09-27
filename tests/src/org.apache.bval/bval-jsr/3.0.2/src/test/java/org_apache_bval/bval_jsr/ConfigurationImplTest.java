/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.util.List;

import jakarta.validation.Configuration;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.apache.bval.jsr.ApacheValidatorConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationImplTest {

    @Test
    void loadsAndCreatesProviderNamedByBootstrapXml() {
        Configuration<?> configuration = Validation.byDefaultProvider()
                .providerResolver(() -> List.of(new ApacheValidationProvider())).configure();
        configuration.addProperty(ApacheValidatorConfiguration.Properties.VALIDATION_XML_PATH,
                "configuration-impl-validation.xml");

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            assertThat(factory.getValidator()).isNotNull();
        }
    }

    public static class AlternateProvider extends ApacheValidationProvider {
    }
}
