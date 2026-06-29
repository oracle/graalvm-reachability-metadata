/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

public class TypeConverterBindingProcessorAnonymous5Test {
    @Test
    void convertsBoundStringConstantToInteger() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("configuredPort")).to("8080");
            }
        });

        Integer configuredPort = injector.getInstance(
                Key.get(Integer.class, Names.named("configuredPort")));

        assertThat(configuredPort).isEqualTo(8080);
    }
}
