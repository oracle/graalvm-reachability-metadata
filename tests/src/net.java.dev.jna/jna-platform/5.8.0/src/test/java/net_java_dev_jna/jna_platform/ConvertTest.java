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
import com.sun.jna.platform.win32.COM.util.IComEnum;
import com.sun.jna.platform.win32.COM.util.IComEventCallbackListener;
import com.sun.jna.platform.win32.COM.util.annotation.ComEventCallback;
import com.sun.jna.platform.win32.COM.util.annotation.ComInterface;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.OaIdl.DISPID;
import com.sun.jna.platform.win32.OaIdl.EXCEPINFO;
import com.sun.jna.platform.win32.OleAuto.DISPPARAMS;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class ConvertTest {
    private static final int ENUM_DISPID = 23;

    @Test
    void toVariantUsesMatchingVariantConstructorForJnaValueTypes() throws Exception {
        Method toVariant = Class.forName("com.sun.jna.platform.win32.COM.util.Convert")
            .getDeclaredMethod("toVariant", Object.class);
        toVariant.setAccessible(true);

        VARIANT variant = (VARIANT) toVariant.invoke(null, new WinDef.BYTE(42));

        assertThat(variant.getVarType().intValue()).isEqualTo(Variant.VT_UI1);
        assertThat(variant.byteValue()).isEqualTo((byte) 42);
    }

    @Test
    void callbackConversionMapsVariantNumbersToComEnums() {
        RecordingEnumListener listener = new RecordingEnumListener();
        CallbackProxy proxy = new CallbackProxy(null, EnumEvents.class, listener);
        DISPPARAMS.ByReference parameters = new DISPPARAMS.ByReference();
        parameters.setArgs(new VARIANT[] { new VARIANT(SampleComEnum.SELECTED.getValue()) });

        HRESULT result = proxy.Invoke(
            new DISPID(ENUM_DISPID),
            new REFIID(Guid.IID_NULL),
            new WinDef.LCID(0),
            new WinDef.WORD(0),
            parameters,
            null,
            new EXCEPINFO.ByReference(),
            new IntByReference()
        );

        assertThat(result).isEqualTo(WinError.S_OK);
        assertThat(listener.received).isEqualTo(SampleComEnum.SELECTED);
        assertThat(listener.errorMessage).isNull();
    }

    @ComInterface(iid = "12345678-1234-1234-1234-1234567890AB")
    public interface EnumEvents {
        @ComEventCallback(dispid = ENUM_DISPID)
        void onEnumValue(SampleComEnum value);
    }

    public enum SampleComEnum implements IComEnum {
        IGNORED(11),
        SELECTED(42);

        private final long value;

        SampleComEnum(long value) {
            this.value = value;
        }

        @Override
        public long getValue() {
            return value;
        }
    }

    public static final class RecordingEnumListener implements EnumEvents, IComEventCallbackListener {
        private SampleComEnum received;
        private String errorMessage;

        @Override
        public void onEnumValue(SampleComEnum value) {
            received = value;
        }

        @Override
        public void setDispatchCallbackListener(IDispatchCallback dispatchCallback) {
        }

        @Override
        public void errorReceivingCallbackEvent(String message, Exception exception) {
            errorMessage = message;
        }
    }
}
