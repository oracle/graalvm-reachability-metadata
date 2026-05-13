/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.ClassConverter;
import org.junit.jupiter.api.Test;

public class ClassConverterTest {
    @Test
    void loadsClassFromConfiguredName() throws ComponentConfigurationException {
        final ClassConverter converter = new ClassConverter();

        final Object value = converter.fromString(LoadedComponent.class.getName());

        assertThat(value).isEqualTo(LoadedComponent.class);
    }

    @Test
    void rejectsMissingClassName() {
        final ClassConverter converter = new ClassConverter();

        assertThatThrownBy(() -> converter.fromString("org.example.DoesNotExist"))
                .isInstanceOf(ComponentConfigurationException.class)
                .hasMessage("Unable to find class in conversion");
    }

    public static final class LoadedComponent {
        public LoadedComponent() {
        }
    }
}
