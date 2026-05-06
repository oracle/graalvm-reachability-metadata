/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

public class BeanWrapperImplInnerBeanPropertyHandlerTest {

    @Test
    @SuppressWarnings("removal")
    public void propertyAccessorInvokesGetterAndSetterWithSecurityManager() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManager(securityManager);

        try {
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
            MutablePropertyBean bean = new MutablePropertyBean();
            BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

            wrapper.setPropertyValue("name", "spring");
            Object value = wrapper.getPropertyValue("name");

            assertThat(value).isEqualTo("spring");
            assertThat(bean.getName()).isEqualTo("spring");
            assertThat(bean.getGetterCalls()).isEqualTo(2);
            assertThat(bean.getSetterCalls()).isEqualTo(1);
            assertThat(securityManagerInstalled).isEqualTo(System.getSecurityManager() == securityManager);
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManager(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException | SecurityException ex) {
            return false;
        }
    }

    public static class MutablePropertyBean {
        private String name;
        private int getterCalls;
        private int setterCalls;

        public String getName() {
            getterCalls++;
            return name;
        }

        public void setName(String name) {
            setterCalls++;
            this.name = name;
        }

        public int getGetterCalls() {
            return getterCalls;
        }

        public int getSetterCalls() {
            return setterCalls;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {

        @Override
        public void checkPermission(Permission permission) {
        }
    }
}
