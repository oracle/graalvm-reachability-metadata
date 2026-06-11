/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.Function;
import com.sun.jna.Pointer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CallbackReferenceInnerDefaultCallbackProxyTest {
    @Test
    void invokesJavaCallbackThroughFunctionPointer() {
        AddCallback callback = (left, right) -> left + right;
        Pointer functionPointer = CallbackReference.getFunctionPointer(callback);
        Function callbackFunction = Function.getFunction(functionPointer);

        Object result = callbackFunction.invoke(Integer.class, new Object[] {19, 23});

        assertThat(result).isEqualTo(42);
    }

    public interface AddCallback extends Callback {
        int invoke(int left, int right);
    }
}
