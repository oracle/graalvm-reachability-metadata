/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_plexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.composite.ArrayConverter;
import org.codehaus.plexus.component.configurator.converters.lookup.DefaultConverterLookup;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.junit.jupiter.api.Test;

public class ArrayConverterTest {
    @Test
    void loadsFullyQualifiedElementTypeAndCreatesObjectArray() throws ComponentConfigurationException {
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("elements");
        configuration.addChild(new DefaultPlexusConfiguration(NamedArrayElement.class.getName()));

        final Object value = new ArrayConverter().fromConfiguration(
                new DefaultConverterLookup(),
                configuration,
                ArrayElement[].class,
                ArrayElement.class,
                ArrayConverterTest.class.getClassLoader(),
                new LiteralExpressionEvaluator(),
                null);

        final ArrayElement[] elements = (ArrayElement[]) value;
        assertThat(elements).hasSize(1);
        assertThat(elements[0]).isInstanceOf(NamedArrayElement.class);
    }

    @Test
    void loadsPackageRelativeElementTypeAndCreatesStringArray() throws ComponentConfigurationException {
        final DefaultPlexusConfiguration configuration = new DefaultPlexusConfiguration("strings");
        configuration.addChild(new DefaultPlexusConfiguration("string", "alpha"));
        configuration.addChild(new DefaultPlexusConfiguration("string", "beta"));

        final Object value = new ArrayConverter().fromConfiguration(
                new DefaultConverterLookup(),
                configuration,
                String[].class,
                Object.class,
                ArrayConverterTest.class.getClassLoader(),
                new LiteralExpressionEvaluator(),
                null);

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly("alpha", "beta");
    }

    public interface ArrayElement {
    }

    public static final class NamedArrayElement implements ArrayElement {
        public NamedArrayElement() {
        }
    }

    private static final class LiteralExpressionEvaluator implements ExpressionEvaluator {
        @Override
        public Object evaluate(final String expression) throws ExpressionEvaluationException {
            return expression;
        }

        @Override
        public File alignToBaseDirectory(final File file) {
            return file;
        }
    }
}
