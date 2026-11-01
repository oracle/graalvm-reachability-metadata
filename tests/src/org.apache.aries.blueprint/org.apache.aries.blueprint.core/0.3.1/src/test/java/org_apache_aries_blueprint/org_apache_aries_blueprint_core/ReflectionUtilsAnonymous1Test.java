/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;

import org.apache.aries.blueprint.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
public class ReflectionUtilsAnonymous1Test {
    @Test
    void invokesLifecycleMethodWithAnAccessControlContext() throws Exception {
        AccessControlContext context = AccessController.getContext();
        Lifecycle bean = new Lifecycle();
        Method start = ReflectionUtils.getLifecycleMethod(Lifecycle.class, "start");
        ReflectionUtils.invoke(context, start, bean);
        assertThat(bean.started).isTrue();
    }

    public static class Lifecycle {
        private boolean started;

        public void start() {
            started = true;
        }
    }
}
