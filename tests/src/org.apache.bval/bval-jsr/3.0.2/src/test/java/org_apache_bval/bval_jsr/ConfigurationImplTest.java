/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bval.bval_jsr;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        AlternateProvider.resetInstancesCreated();
        Configuration<?> configuration = Validation.byDefaultProvider()
                .providerResolver(() -> List.of(new ApacheValidationProvider())).configure();
        configuration.addProperty(ApacheValidatorConfiguration.Properties.VALIDATION_XML_PATH,
                "configuration-impl-validation.xml");

        try (ValidatorFactory factory = configuration.buildValidatorFactory()) {
            assertThat(factory.getValidator()).isNotNull();
            assertThat(AlternateProvider.instancesCreated()).isOne();
        }
    }

    public static class AlternateProvider extends ApacheValidationProvider {

        private static final AtomicInteger INSTANCES_CREATED = new AtomicInteger();

        public AlternateProvider() {
            INSTANCES_CREATED.incrementAndGet();
        }

        static void resetInstancesCreated() {
            INSTANCES_CREATED.set(0);
        }

        static int instancesCreated() {
            return INSTANCES_CREATED.get();
        }
    }
}
