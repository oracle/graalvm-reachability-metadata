/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.OaIdl.DISPID;
import com.sun.jna.platform.win32.OleAuto.DISPPARAMS;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.Variant.VariantArg;
import com.sun.jna.platform.win32.WinDef.LCID;
import com.sun.jna.platform.win32.WinDef.UINT;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.COM.IDispatchCallback;
import com.sun.jna.platform.win32.COM.util.CallbackProxy;
import com.sun.jna.platform.win32.COM.util.IComEventCallbackListener;
import com.sun.jna.platform.win32.COM.util.annotation.ComInterface;
import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import sun.misc.Unsafe;

public class CallbackProxyTest {
    @Test
    void invokeDispatchesAnnotatedComEventMethod() throws Throwable {
        RecordingListener listener = new RecordingListener();
        CallbackProxy callback = callbackProxyFor(listener);
        DISPPARAMS.ByReference parameters = dispatchParameters(new VARIANT(321));

        invokeOnThread(callback, new DISPID(42), parameters);

        assertThat(listener.receivedValue).isEqualTo(321);
        assertThat(listener.errorMessage).isNull();
        assertThat(listener.error).isNull();
        assertThat(listener.dispatchCallback).isNull();
    }

    private static CallbackProxy callbackProxyFor(RecordingListener listener) throws Throwable {
        // The constructor creates a Windows stdcall vtable; initialize only the Java dispatch state.
        CallbackProxy callback = allocateCallbackProxy();
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(CallbackProxy.class, MethodHandles.lookup());
        VarHandle listenerHandle = lookup.findVarHandle(CallbackProxy.class, "comEventCallbackListener",
                IComEventCallbackListener.class);
        listenerHandle.set(callback, listener);

        MethodHandle createDispIdMap = lookup.findVirtual(CallbackProxy.class, "createDispIdMap",
                MethodType.methodType(Map.class, Class.class));
        @SuppressWarnings("unchecked")
        Map<DISPID, Method> dispIdMap = (Map<DISPID, Method>) createDispIdMap.invoke(callback,
                RecordingEvents.class);

        VarHandle dispIdMapHandle = lookup.findVarHandle(CallbackProxy.class, "dsipIdMap", Map.class);
        dispIdMapHandle.set(callback, dispIdMap);
        return callback;
    }

    private static void invokeOnThread(CallbackProxy callback, DISPID dispId, DISPPARAMS.ByReference parameters)
            throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(CallbackProxy.class, MethodHandles.lookup());
        MethodHandle invokeOnThread = lookup.findVirtual(CallbackProxy.class, "invokeOnThread",
                MethodType.methodType(void.class, DISPID.class, REFIID.class, LCID.class, WORD.class,
                        DISPPARAMS.ByReference.class));
        invokeOnThread.invoke(callback, dispId, null, new LCID(0), new WORD(0), parameters);
    }

    private static CallbackProxy allocateCallbackProxy() throws Exception {
        return allocateInstance(CallbackProxy.class);
    }

    private static DISPPARAMS.ByReference dispatchParameters(VARIANT argument) throws Exception {
        DISPPARAMS.ByReference parameters = allocateInstance(DISPPARAMS.ByReference.class);
        parameters.rgvarg = new InMemoryVariantArg(argument);
        parameters.cArgs = new UINT(1);
        parameters.cNamedArgs = new UINT(0);
        return parameters;
    }

    private static <T> T allocateInstance(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return type.cast(unsafe.allocateInstance(type));
    }

    @ComInterface(iid = "{12345678-1234-1234-1234-1234567890AB}")
    public interface RecordingEvents {
        @ComMethod(dispId = 42)
        void valueChanged(int value);
    }

    private static final class InMemoryVariantArg extends VariantArg.ByReference {
        private InMemoryVariantArg(VARIANT argument) {
            this.variantArg = new VARIANT[] {argument};
        }

        @Override
        public void setArraySize(int size) {
        }
    }

    private static final class RecordingListener implements RecordingEvents, IComEventCallbackListener {
        private int receivedValue = -1;
        private String errorMessage;
        private Exception error;
        private IDispatchCallback dispatchCallback;

        @Override
        public void valueChanged(int value) {
            this.receivedValue = value;
        }

        @Override
        public void setDispatchCallbackListener(IDispatchCallback dispatchCallback) {
            this.dispatchCallback = dispatchCallback;
        }

        @Override
        public void errorReceivingCallbackEvent(String message, Exception exception) {
            this.errorMessage = message;
            this.error = exception;
        }
    }
}
