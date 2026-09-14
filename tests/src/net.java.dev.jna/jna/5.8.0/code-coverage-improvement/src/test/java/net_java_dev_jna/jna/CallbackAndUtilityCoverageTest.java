/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Comparator;

import com.sun.jna.Callback;
import com.sun.jna.CallbackReference;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.DefaultTypeMapper;
import com.sun.jna.FromNativeContext;
import com.sun.jna.FromNativeConverter;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ToNativeConverter;
import com.sun.jna.WeakMemoryHolder;
import com.sun.jna.Memory;
import com.sun.jna.Platform;
import com.sun.jna.win32.StdCallFunctionMapper;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.internal.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallbackAndUtilityCoverageTest {
    public interface Adder extends Callback {
        int invoke(int value);
    }

    public interface StructureCallback extends Callback {
        void callback(StructureCoverageTest.Record record);
    }

    public interface DefaultGreeting {
        default String greet(String name) {
            return "Hello " + name;
        }
    }

    @Test
    void callbackPointersAndThreadSettings() throws Exception {
        Adder callback = value -> value + 1;
        Pointer functionPointer = CallbackReference.getFunctionPointer(callback);
        assertThat(functionPointer).isNotNull();
        Adder recovered = (Adder) CallbackReference.getCallback(Adder.class, functionPointer);
        assertThat(recovered.invoke(4)).isEqualTo(5);
        Adder external = (Adder) CallbackReference.getCallback(Adder.class, Pointer.createConstant(0x3456L));
        assertThat(external).isNotNull();
        assertThat(Pointer.nativeValue(CallbackReference.getFunctionPointer(external))).isEqualTo(0x3456L);

        ThreadGroup group = new ThreadGroup("jna-test");
        CallbackThreadInitializer defaults = new CallbackThreadInitializer();
        CallbackThreadInitializer daemon = new CallbackThreadInitializer(false);
        CallbackThreadInitializer detached = new CallbackThreadInitializer(false, true);
        CallbackThreadInitializer named = new CallbackThreadInitializer(true, false, "jna-callback");
        CallbackThreadInitializer complete = new CallbackThreadInitializer(false, true, "jna-detached", group);
        assertThat(defaults.isDaemon(callback)).isTrue();
        assertThat(daemon.isDaemon(callback)).isFalse();
        assertThat(detached.detach(callback)).isTrue();
        assertThat(named.getName(callback)).isEqualTo("jna-callback");
        assertThat(complete.getName(callback)).isEqualTo("jna-detached");
        assertThat(complete.getThreadGroup(callback)).isSameAs(group);
        assertThat(complete.isDaemon(callback)).isFalse();
        Native.setCallbackThreadInitializer(callback, complete);
        assertThat(CallbackReference.getFunctionPointer(callback)).isNotNull();
        Native.setCallbackThreadInitializer(callback, null);
        Adder initialized = value -> value - 1;
        Native.setCallbackThreadInitializer(initialized, complete);
        assertThat(CallbackReference.getFunctionPointer(initialized)).isNotNull();
        Native.setCallbackThreadInitializer(initialized, null);

        StructureCallback structureCallback = record -> {
        };
        assertThat(CallbackReference.getFunctionPointer(structureCallback)).isNotNull();
    }

    @Test
    void defaultMethodsCanBeInvokedThroughJnaReflectionUtility() throws Throwable {
        DefaultGreeting target = new DefaultGreeting() {
        };
        Method method = DefaultGreeting.class.getMethod("greet", String.class);
        assertThat(ReflectionUtils.isDefault(method)).isTrue();
        Object handle = ReflectionUtils.getMethodHandle(method);
        assertThat(handle).isNotNull();
        assertThat(ReflectionUtils.invokeDefaultMethod(target, handle, "JNA")).isEqualTo("Hello JNA");
        assertThat(new ReflectionUtils()).isNotNull();

        try {
            ReflectionUtils.getMethodHandle(Comparator.class.getMethod("reversed"));
        } catch (Throwable expected) {
            assertThat(expected).isNotNull();
        }
    }

    @Test
    void typeMapperAndLibraryHandlerExposeConfiguredValues() throws Exception {
        DefaultTypeMapper mapper = new DefaultTypeMapper();
        ToNativeConverter toNative = new ToNativeConverter() {
            @Override
            public Object toNative(Object value, com.sun.jna.ToNativeContext context) {
                return value.toString().length();
            }

            @Override
            public Class<?> nativeType() {
                return Integer.class;
            }
        };
        FromNativeConverter fromNative = new FromNativeConverter() {
            @Override
            public Object fromNative(Object value, FromNativeContext context) {
                return "value=" + value;
            }

            @Override
            public Class<?> nativeType() {
                return Integer.class;
            }
        };
        mapper.addToNativeConverter(String.class, toNative);
        mapper.addFromNativeConverter(String.class, fromNative);
        mapper.addTypeConverter(Integer.class, new com.sun.jna.TypeConverter() {
            @Override
            public Object toNative(Object value, com.sun.jna.ToNativeContext context) {
                return value;
            }

            @Override
            public Object fromNative(Object value, FromNativeContext context) {
                return value;
            }

            @Override
            public Class<?> nativeType() {
                return Integer.class;
            }
        });
        assertThat(mapper.getToNativeConverter(String.class)).isSameAs(toNative);
        assertThat(mapper.getFromNativeConverter(String.class)).isSameAs(fromNative);

        Library library = Native.load("c", FunctionNativeCoverageTest.Posix.class);
        Library.Handler handler = (Library.Handler) Proxy.getInvocationHandler(library);
        assertThat(handler.getInterfaceClass()).isEqualTo(FunctionNativeCoverageTest.Posix.class);
        assertThat(handler.getLibraryName()).isNotEmpty();
        assertThat(handler.getNativeLibrary()).isNotNull();
    }

    private static final class UnicodeMapper extends W32APIFunctionMapper {
        UnicodeMapper() {
            super(true);
        }
    }

    @Test
    void platformMappersAndWeakMemoryHolderWork() throws Exception {
        assertThat(Platform.hasRuntimeExec()).isTrue();
        assertThat(Platform.isFreeBSD()).isFalse();
        assertThat(Platform.isNetBSD()).isFalse();
        assertThat(Platform.isOpenBSD()).isFalse();
        assertThat(Platform.isWindowsCE()).isFalse();
        assertThat(Platform.isX11()).isEqualTo(Platform.isLinux());

        NativeLibrary library = NativeLibrary.getInstance("c");
        Method method = FunctionNativeCoverageTest.Posix.class.getMethod("atol", String.class);
        assertThat(new StdCallFunctionMapper().getFunctionName(library, method)).isEqualTo("atol");
        assertThat(new UnicodeMapper().getFunctionName(library, method)).isEqualTo("atol");

        WeakMemoryHolder holder = new WeakMemoryHolder();
        Object key = new Object();
        holder.put(key, new Memory(4));
        holder.clean();

    }
}
