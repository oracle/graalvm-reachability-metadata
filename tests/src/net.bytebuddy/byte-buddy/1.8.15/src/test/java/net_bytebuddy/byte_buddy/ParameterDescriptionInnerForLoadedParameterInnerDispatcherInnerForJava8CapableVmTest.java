/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.method.ParameterList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterDescriptionInnerForLoadedParameterInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void readsLoadedMethodParameterPropertiesThroughByteBuddyDescription() throws Exception {
        Method method = ParameterCarrier.class.getDeclaredMethod("describe", String.class, int.class);
        Parameter reflectionParameter = method.getParameters()[0];
        ParameterList<ParameterDescription.InDefinedShape> parameters = new MethodDescription.ForLoadedMethod(method)
                .getParameters();

        ParameterDescription.InDefinedShape byteBuddyParameter = parameters.get(0);

        assertThat(parameters).hasSize(2);
        assertThat(byteBuddyParameter.getName()).isEqualTo(reflectionParameter.getName());
        assertThat(byteBuddyParameter.isNamed()).isEqualTo(reflectionParameter.isNamePresent());
        assertThat(byteBuddyParameter.getModifiers()).isEqualTo(reflectionParameter.getModifiers());
        assertThat(byteBuddyParameter.getIndex()).isZero();
    }

    private static class ParameterCarrier {
        String describe(String label, int count) {
            return label + count;
        }
    }
}
