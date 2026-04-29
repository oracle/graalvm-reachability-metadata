/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.seata.integration.tx.api.util.ClassUtils;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {
    @Test
    void resolvesPublicInterfaceMethodToImplementationMethod() throws Exception {
        Method interfaceMethod = TransactionalService.class.getMethod("apply", String.class);

        Method specificMethod = ClassUtils.getMostSpecificMethod(interfaceMethod, TransactionalServiceImpl.class);

        assertThat(specificMethod.getDeclaringClass()).isEqualTo(TransactionalServiceImpl.class);
        assertThat(specificMethod.getName()).isEqualTo(interfaceMethod.getName());
        assertThat(specificMethod.getParameterTypes()).containsExactly(String.class);
    }

    public interface TransactionalService {
        String apply(String value);
    }

    public static class TransactionalServiceImpl implements TransactionalService {
        @Override
        public String apply(String value) {
            return "applied:" + value;
        }
    }
}
