/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.lang.reflect.Method;

import org.apache.seata.integration.tx.api.util.ClassUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilsTest {
    @Test
    void getMostSpecificMethodReturnsTheOverrideFromTheTargetClass() throws NoSuchMethodException {
        Method baseMethod = BaseService.class.getMethod("handle", String.class);

        Method specificMethod = ClassUtils.getMostSpecificMethod(baseMethod, SpecializedService.class);

        assertThat(specificMethod).isEqualTo(SpecializedService.class.getMethod("handle", String.class));
        assertThat(specificMethod.getDeclaringClass()).isEqualTo(SpecializedService.class);
    }

    @Test
    void getPackageNameSupportsClassAndPlainTypeNames() {
        assertThat(ClassUtils.getPackageName(SpecializedService.class)).isEqualTo("org_apache_seata.seata_all");
        assertThat(ClassUtils.getPackageName("SimpleTypeName")).isEmpty();
    }

    public static class BaseService {
        public String handle(String value) {
            return "base-" + value;
        }
    }

    public static final class SpecializedService extends BaseService {
        @Override
        public String handle(String value) {
            return "specialized-" + value;
        }
    }
}
