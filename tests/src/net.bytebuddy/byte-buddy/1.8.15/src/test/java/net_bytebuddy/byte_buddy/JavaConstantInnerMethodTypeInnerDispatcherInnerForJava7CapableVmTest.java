/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaConstantInnerMethodTypeInnerDispatcherInnerForJava7CapableVmTest {
    @Test
    void describesLoadedMethodType() {
        MethodType loadedMethodType = MethodType.methodType(String.class, int.class, long.class);

        JavaConstant.MethodType methodType = JavaConstant.MethodType.ofLoaded(loadedMethodType);
        TypeList parameterTypes = methodType.getParameterTypes();

        assertThat(methodType.getReturnType().represents(String.class)).isTrue();
        assertThat(parameterTypes).hasSize(2);
        assertThat(parameterTypes.get(0).represents(int.class)).isTrue();
        assertThat(parameterTypes.get(1).represents(long.class)).isTrue();
        assertThat(methodType.getDescriptor()).isEqualTo("(IJ)Ljava/lang/String;");
        assertThat(methodType.asConstantPoolValue()).isNotNull();
    }
}
