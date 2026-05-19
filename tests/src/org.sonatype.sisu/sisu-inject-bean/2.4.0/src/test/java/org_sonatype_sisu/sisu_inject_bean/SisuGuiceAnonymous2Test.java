/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SisuGuiceAnonymous2Test {
    @Test
    @SuppressWarnings("deprecation")
    void enhancedInjectorDelegatesUnmatchedMethodCallsToWrappedInjector() {
        Injector injector = Guice.createInjector();
        Injector enhancedInjector = SisuGuice.enhance(injector);

        assertThat(enhancedInjector.getParent()).isSameAs(injector.getParent());
    }
}
