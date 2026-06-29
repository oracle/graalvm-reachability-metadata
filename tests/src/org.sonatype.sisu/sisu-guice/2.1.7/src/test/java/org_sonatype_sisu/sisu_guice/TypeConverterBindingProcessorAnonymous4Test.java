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
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class TypeConverterBindingProcessorAnonymous4Test {
    @Test
    void convertsBoundConstantToClassLiteral() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bindConstant().annotatedWith(Names.named("configuredClass")).to("java.util.ArrayList");
            }
        });

        Class<?> configuredClass = injector.getInstance(Key.get(Class.class, Names.named("configuredClass")));

        assertThat(configuredClass).isEqualTo(ArrayList.class);
    }
}
