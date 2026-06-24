/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.inject.BeanLocator;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

@SuppressWarnings("deprecation")
public class SisuGuiceAnonymous2Test {
    @Test
    void enhancedInjectorDelegatesUnspecializedInjectorMethods() {
        DefaultBeanLocator locator = new DefaultBeanLocator();
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BeanLocator.class).toInstance(locator);
            }
        });

        Injector enhancedInjector = SisuGuice.enhance(injector);

        assertThat(enhancedInjector.getBindings()).containsKey(Key.get(BeanLocator.class));
    }
}
