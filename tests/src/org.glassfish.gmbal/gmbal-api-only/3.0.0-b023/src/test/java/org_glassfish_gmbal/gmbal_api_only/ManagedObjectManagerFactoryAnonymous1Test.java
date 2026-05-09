/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_gmbal.gmbal_api_only;

import java.lang.reflect.Method;

import org.glassfish.gmbal.ManagedObjectManager;
import org.glassfish.gmbal.ManagedObjectManagerFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedObjectManagerFactoryAnonymous1Test {
    @Test
    void getMethodFindsDeclaredFactoryMethod() {
        Method method = ManagedObjectManagerFactory.getMethod(ManagedObjectManagerFactory.class, "createNOOP");

        assertThat(method.getName()).isEqualTo("createNOOP");
        assertThat(method.getReturnType()).isEqualTo(ManagedObjectManager.class);
        assertThat(method.getParameterCount()).isZero();
    }
}
