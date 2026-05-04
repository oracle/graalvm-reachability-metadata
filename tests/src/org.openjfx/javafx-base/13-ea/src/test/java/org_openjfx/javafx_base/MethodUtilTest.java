/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjfx.javafx_base;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.javafx.reflect.MethodUtil;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MethodUtilTest {

    @Test
    void publicLookupMethodsReturnInvocableBeanMethods() throws Exception {
        Method getter = MethodUtil.getMethod(AccessibleBean.class, "getName", new Class<?>[0]);
        Method setter = MethodUtil.getMethod(AccessibleBean.class, "setName", new Class<?>[] {String.class});
        AccessibleBean bean = new AccessibleBean("Ada");

        MethodUtil.invoke(setter, bean, new Object[] {"Grace"});
        Object name = MethodUtil.invoke(getter, bean, null);
        Method[] methods = MethodUtil.getMethods(AccessibleBean.class);

        assertThat(name).isEqualTo("Grace");
        assertThat(methods)
                .extracting(Method::getName)
                .contains("getName", "setName", "describe");
    }

    @Test
    void publicMethodsFastPathEnumeratesAccessibleMethodsWithoutSecurityManager() throws Exception {
        Method[] methods = invokeGetPublicMethods(AccessibleBean.class);

        assertThat(methods)
                .extracting(Method::getName)
                .contains("getName", "setName", "describe");
    }

    @Test
    void internalPublicMethodScanCollectsInterfaceAndImplementationMethods() throws Exception {
        Map<Object, Method> signatures = new HashMap<>();
        Method getInternalPublicMethods = MethodUtil.class.getDeclaredMethod(
                "getInternalPublicMethods", Class.class, Map.class);
        getInternalPublicMethods.setAccessible(true);

        boolean complete = (boolean) getInternalPublicMethods.invoke(null, AccessibleBean.class, signatures);

        assertThat(complete).isTrue();
        assertThat(signatures.values())
                .extracting(Method::getName)
                .contains("getName", "setName", "describe");
    }

    private static Method[] invokeGetPublicMethods(Class<?> type) throws Exception {
        Method getPublicMethods = MethodUtil.class.getDeclaredMethod("getPublicMethods", Class.class);
        getPublicMethods.setAccessible(true);
        return (Method[]) getPublicMethods.invoke(null, type);
    }

    public interface Described {
        String describe();
    }

    public static final class AccessibleBean implements Described {
        private String name;

        public AccessibleBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String describe() {
            return "bean:" + name;
        }
    }
}
