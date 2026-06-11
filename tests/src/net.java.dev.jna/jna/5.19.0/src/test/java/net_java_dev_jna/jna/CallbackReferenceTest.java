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
import com.sun.jna.NativeLibrary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CallbackReferenceTest {
    @Test
    void createsCallbackProxyForNativeFunctionPointer() {
        Function atol = NativeLibrary.getInstance("c").getFunction("atol");

        AtolCallback callback = (AtolCallback) CallbackReference.getCallback(AtolCallback.class, atol);

        assertThat(callback.atol("42")).isEqualTo(42);
        assertThat(CallbackReference.getCallback(AtolCallback.class, atol)).isSameAs(callback);
    }

    public interface AtolCallback extends Callback {
        int atol(String value);
    }
}
