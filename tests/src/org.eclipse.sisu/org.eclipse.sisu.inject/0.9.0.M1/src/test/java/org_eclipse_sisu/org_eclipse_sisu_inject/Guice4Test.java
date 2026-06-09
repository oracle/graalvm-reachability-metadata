/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.Parameters;
import org.eclipse.sisu.wire.WireModule;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;

public class Guice4Test {
    @Test
    void wireModuleMergesParametersFromProvidesMethodBinding() throws Exception {
        registerGuicePrimitiveParserMethods();

        Injector injector = Guice.createInjector(new WireModule(new AbstractModule() {
            @Override
            protected void configure() {
                bind(NeedsArguments.class);
            }

            @Provides
            @Parameters
            String[] applicationArguments() {
                return new String[] {"--repository=central", "--offline=false"};
            }
        }));

        NeedsArguments needsArguments = injector.getInstance(NeedsArguments.class);

        assertThat(needsArguments.arguments()).containsExactly("--repository=central", "--offline=false");
    }

    private static void registerGuicePrimitiveParserMethods() throws NoSuchMethodException {
        Integer.class.getMethod("parseInt", String.class);
        Long.class.getMethod("parseLong", String.class);
        Boolean.class.getMethod("parseBoolean", String.class);
        Byte.class.getMethod("parseByte", String.class);
        Short.class.getMethod("parseShort", String.class);
        Float.class.getMethod("parseFloat", String.class);
        Double.class.getMethod("parseDouble", String.class);
    }

    private static final class NeedsArguments {
        private final String[] arguments;

        @Inject
        private NeedsArguments(@Parameters String[] arguments) {
            this.arguments = arguments;
        }

        private String[] arguments() {
            return arguments;
        }
    }
}
