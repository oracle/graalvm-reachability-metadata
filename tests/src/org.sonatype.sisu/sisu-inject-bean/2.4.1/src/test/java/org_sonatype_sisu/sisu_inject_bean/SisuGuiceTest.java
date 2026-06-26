/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.eclipse.sisu.inject.BeanLocator;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

@SuppressWarnings("deprecation")
public class SisuGuiceTest {
    @Test
    void enhanceReturnsProxyInjector() {
        DefaultBeanLocator locator = new DefaultBeanLocator();
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(BeanLocator.class).toInstance(locator);
            }
        });

        Injector enhancedInjector = SisuGuice.enhance(injector);

        assertThat(enhancedInjector).isInstanceOf(Injector.class);
        assertThat(Proxy.isProxyClass(enhancedInjector.getClass())).isTrue();
    }
}
