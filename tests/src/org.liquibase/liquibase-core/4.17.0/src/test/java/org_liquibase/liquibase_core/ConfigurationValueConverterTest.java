/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.configuration.ConfigurationValueConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ConfigurationValueConverterTest {

    @Test
    void classConverterLoadsNamedClass() {
        Class<?> convertedClass = ConfigurationValueConverter.CLASS.convert("java.lang.String");

        assertThat(convertedClass).isEqualTo(String.class);
    }

    @Test
    void classConverterRejectsUnknownClassName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationValueConverter.CLASS.convert("example.missing.DoesNotExist"))
                .withMessageContaining("Cannot instantiate example.missing.DoesNotExist");
    }
}
