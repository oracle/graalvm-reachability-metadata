/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.COM.IDispatchCallback;
import com.sun.jna.platform.win32.COM.util.CallbackProxy;
import com.sun.jna.platform.win32.COM.util.IComEventCallbackListener;
import com.sun.jna.platform.win32.COM.util.ObjectFactory;
import com.sun.jna.platform.win32.COM.util.annotation.ComInterface;
import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.OaIdl.DISPID;
import com.sun.jna.platform.win32.OleAuto.DISPPARAMS;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinDef.LCID;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class CallbackProxyTest {

    private static final int STATUS_CHANGED_DISPID = 17;

    @Test
    void invokesAnnotatedEventCallbackThroughDispatchParameters() throws Throwable {
        disableComInitializationAssertion();
        RecordingEventListener listener = new RecordingEventListener();
        CallbackProxy proxy = allocateCallbackProxy();
        setCallbackProxyField(proxy, "factory", ObjectFactory.class, null);
        setCallbackProxyField(proxy, "comEventCallbackInterface", Class.class, SampleEvents.class);
        setCallbackProxyField(proxy, "comEventCallbackListener", IComEventCallbackListener.class, listener);
        setCallbackProxyField(proxy, "dsipIdMap", Map.class, createDispIdMap(proxy));
        DISPPARAMS.ByReference parameters = dispatchParameters(new VARIANT[] { new VARIANT(true), new VARIANT(7) });

        HRESULT result = proxy.Invoke(
            new DISPID(STATUS_CHANGED_DISPID),
            null,
            new LCID(0),
            new WORD(0),
            parameters,
            null,
            null,
            null
        );

        assertThat(result).isEqualTo(WinError.S_OK);
        assertThat(listener.errorMessage).isNull();
        assertThat(listener.statusCode).isEqualTo(7);
        assertThat(listener.active).isTrue();
        assertThat(listener.invocationCount).isEqualTo(1);
    }

    private static CallbackProxy allocateCallbackProxy() throws Throwable {
        return (CallbackProxy) unsafe().allocateInstance(CallbackProxy.class);
    }

    private static DISPPARAMS.ByReference dispatchParameters(VARIANT[] arguments) throws Throwable {
        TestDispatchParameters parameters = (TestDispatchParameters) unsafe().allocateInstance(TestDispatchParameters.class);
        parameters.arguments = arguments;
        parameters.namedArguments = new DISPID[0];
        return parameters;
    }

    private static Unsafe unsafe() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup());
        VarHandle varHandle = lookup.findStaticVarHandle(Unsafe.class, "theUnsafe", Unsafe.class);
        return (Unsafe) varHandle.get();
    }

    private static void setCallbackProxyField(CallbackProxy proxy, String fieldName, Class<?> fieldType, Object value) throws Throwable {
        VarHandle varHandle = callbackProxyLookup().findVarHandle(CallbackProxy.class, fieldName, fieldType);
        varHandle.set(proxy, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<DISPID, Method> createDispIdMap(CallbackProxy proxy) throws Throwable {
        MethodType methodType = MethodType.methodType(Map.class, Class.class);
        MethodHandle methodHandle = callbackProxyLookup().findVirtual(CallbackProxy.class, "createDispIdMap", methodType);
        return (Map<DISPID, Method>) methodHandle.invoke(proxy, SampleEvents.class);
    }

    private static MethodHandles.Lookup callbackProxyLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(CallbackProxy.class, MethodHandles.lookup());
    }

    private static void disableComInitializationAssertion() {
        CallbackProxyTest.class.getClassLoader().setClassAssertionStatus("com.sun.jna.platform.win32.COM.util.CallbackProxy", false);
    }

    @ComInterface(iid = "00000000-0000-0000-C000-000000000046")
    public interface SampleEvents {

        @ComMethod(dispId = STATUS_CHANGED_DISPID)
        void onStatusChanged(int statusCode, boolean active);

    }

    public static class TestDispatchParameters extends DISPPARAMS.ByReference {

        private VARIANT[] arguments;
        private DISPID[] namedArguments;

        @Override
        public VARIANT[] getArgs() {
            return arguments;
        }

        @Override
        public DISPID[] getRgdispidNamedArgs() {
            return namedArguments;
        }
    }

    public static class RecordingEventListener implements SampleEvents, IComEventCallbackListener {

        private int statusCode;
        private boolean active;
        private int invocationCount;
        private String errorMessage;

        @Override
        public void onStatusChanged(int statusCode, boolean active) {
            this.statusCode = statusCode;
            this.active = active;
            this.invocationCount++;
        }

        @Override
        public void setDispatchCallbackListener(IDispatchCallback dispatchCallback) {
        }

        @Override
        public void errorReceivingCallbackEvent(String message, Exception exception) {
            this.errorMessage = message;
        }
    }

}
