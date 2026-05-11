/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import ch.qos.logback.core.util.OptionHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptionHelperTest {
    @Test
    void instantiateClassWithSuperclassRestrictionUsesPublicNoArgConstructor() throws Exception {
        Object instance = OptionHelper.instantiateClassWithSuperclassRestriction(NoArgComponent.class, Component.class);

        assertThat(instance).isInstanceOf(NoArgComponent.class);
        assertThat(((NoArgComponent) instance).name()).isEqualTo("no-arg");
    }

    @Test
    void instantiateByClassNameUsesClassLoaderAndPublicNoArgConstructor() throws Exception {
        Object instance = OptionHelper.instantiateByClassNameAndParameter(
                NoArgComponent.class.getName(),
                Component.class,
                OptionHelperTest.class.getClassLoader(),
                null,
                null);

        assertThat(instance).isInstanceOf(NoArgComponent.class);
        assertThat(((NoArgComponent) instance).name()).isEqualTo("no-arg");
    }

    @Test
    void instantiateByClassNameUsesPublicParameterizedConstructor() throws Exception {
        Object instance = OptionHelper.instantiateByClassNameAndParameter(
                NamedComponent.class.getName(),
                Component.class,
                OptionHelperTest.class.getClassLoader(),
                String.class,
                "configured-name");

        assertThat(instance).isInstanceOf(NamedComponent.class);
        assertThat(((NamedComponent) instance).name()).isEqualTo("configured-name");
    }

    public interface Component {
        String name();
    }

    public static class NoArgComponent implements Component {
        public NoArgComponent() {
        }

        @Override
        public String name() {
            return "no-arg";
        }
    }

    public static class NamedComponent implements Component {
        private final String name;

        public NamedComponent(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }
    }
}
