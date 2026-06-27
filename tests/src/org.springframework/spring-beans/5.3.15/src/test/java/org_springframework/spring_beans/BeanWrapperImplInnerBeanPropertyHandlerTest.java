/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.security.AccessController;
import java.security.Permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanWrapperImplInnerBeanPropertyHandlerTest {

    @Test
    void propertyAccessInvokesReadableAndWritableBeanProperties() {
        MutableBean bean = new MutableBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

        wrapper.setPropertyValue("name", "spring");
        Object value = wrapper.getPropertyValue("name");

        assertThat(value).isEqualTo("spring");
        assertThat(bean.getName()).isEqualTo("spring");
    }

    @Test
    @SuppressWarnings("removal")
    void propertyAccessUsesSecurityContextWhenSecurityManagerIsAvailable() {
        SecurityManager previousSecurityManager = System.getSecurityManager();
        SecurityManager securityManager = new PermissiveSecurityManager();
        boolean securityManagerInstalled = installSecurityManagerIfSupported(securityManager);

        try {
            MutableBean bean = new MutableBean();
            BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
            wrapper.setSecurityContext(AccessController.getContext());

            wrapper.setPropertyValue("name", "secured");
            Object value = wrapper.getPropertyValue("name");

            assertThat(value).isEqualTo("secured");
            assertThat(bean.getName()).isEqualTo("secured");
            if (securityManagerInstalled) {
                assertThat(System.getSecurityManager()).isSameAs(securityManager);
            }
        } finally {
            if (securityManagerInstalled) {
                System.setSecurityManager(previousSecurityManager);
            }
        }
    }

    @SuppressWarnings("removal")
    private static boolean installSecurityManagerIfSupported(SecurityManager securityManager) {
        try {
            System.setSecurityManager(securityManager);
            return System.getSecurityManager() == securityManager;
        } catch (UnsupportedOperationException unsupportedOperationException) {
            return false;
        }
    }

    @SuppressWarnings("removal")
    private static class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
        }

        @Override
        public void checkPermission(Permission permission, Object context) {
        }
    }

    public static class MutableBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
