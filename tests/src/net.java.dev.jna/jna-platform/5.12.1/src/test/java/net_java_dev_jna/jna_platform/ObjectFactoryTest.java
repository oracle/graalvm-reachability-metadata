/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.Dispatch;
import com.sun.jna.platform.win32.COM.util.ObjectFactory;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;

public class ObjectFactoryTest {
    private static NativeLibrary kernel32Facade;

    @Test
    void objectFactoryCreatesJavaProxyForDispatchInterface() throws Exception {
        disableComInitializationAssertions();
        installKernel32Facade();
        ObjectFactory factory = new ObjectFactory();
        FakeDispatch dispatch = new FakeDispatch();

        try {
            TestComInterface proxy = factory.createProxy(TestComInterface.class, dispatch);

            assertThat(TestComInterface.class.isInstance(proxy)).isTrue();
            assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
        } finally {
            factory.disposeAll();
        }
    }

    public interface TestComInterface {
    }

    static void disableComInitializationAssertions() {
        ObjectFactoryTest.class.getClassLoader()
                .setPackageAssertionStatus("com.sun.jna.platform.win32.COM.util", false);
    }

    static void installKernel32Facade() throws Exception {
        ClassLoader kernel32ClassLoader = Class.forName("com.sun.jna.platform.win32.Kernel32", false,
                ObjectFactoryTest.class.getClassLoader()).getClassLoader();
        Map<String, Object> lookupOptions = kernel32Options(kernel32ClassLoader);
        Map<String, Object> processOptions = new HashMap<>(lookupOptions);
        processOptions.put("calling-convention", Function.C_CONVENTION);
        NativeLibrary processLibrary = NativeLibrary.getProcess(processOptions);
        Function getpid = Function.getFunction("c", "getpid", Function.C_CONVENTION);
        int callFlags = intField(processLibrary, "callFlags");
        String encoding = (String) field(processLibrary, "encoding");
        Map<String, Function> functions = functions(processLibrary);
        functions.put(functionKey("GetUserDefaultLCID", callFlags, encoding), getpid);
        functions.put(functionKey("GetUserDefaultLCIDW", callFlags, encoding), getpid);
        functions.put(functionKey("GetUserDefaultLCIDA", callFlags, encoding), getpid);

        kernel32Facade = processLibrary;
        Map<String, WeakReference<NativeLibrary>> libraries = libraries();
        WeakReference<NativeLibrary> reference = new WeakReference<>(kernel32Facade);
        libraries.put("kernel32" + lookupOptions, reference);
        libraries.put("kernel32" + new HashMap<>(lookupOptions), reference);
        libraries.put("kernel32" + kernel32Options(kernel32ClassLoader), reference);
        libraries.put("kernel32" + W32APIOptions.DEFAULT_OPTIONS, reference);
    }

    private static Map<String, Object> kernel32Options(ClassLoader kernel32ClassLoader) {
        Map<String, Object> options = new HashMap<>(W32APIOptions.DEFAULT_OPTIONS);
        if (!options.containsKey("calling-convention")) {
            options.put("calling-convention", Function.ALT_CONVENTION);
        }
        if (!options.containsKey("classloader")) {
            options.put("classloader", kernel32ClassLoader);
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Function> functions(NativeLibrary library) throws Exception {
        return (Map<String, Function>) field(library, "functions");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, WeakReference<NativeLibrary>> libraries() throws Exception {
        Field field = NativeLibrary.class.getDeclaredField("libraries");
        field.setAccessible(true);
        return (Map<String, WeakReference<NativeLibrary>>) field.get(null);
    }

    private static int intField(NativeLibrary library, String name) throws Exception {
        return ((Number) field(library, name)).intValue();
    }

    private static Object field(NativeLibrary library, String name) throws Exception {
        Field field = NativeLibrary.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(library);
    }

    private static String functionKey(String name, int callFlags, String encoding) {
        return name + "|" + callFlags + "|" + encoding;
    }

    static final class FakeDispatch extends Dispatch {
        private final Memory dispatchObject;
        private final Memory vtable;
        private int referenceCount;

        FakeDispatch() {
            this.dispatchObject = new Memory(Native.POINTER_SIZE);
            this.vtable = new Memory(3L * Native.POINTER_SIZE);
            Function getpid = Function.getFunction("c", "getpid", Function.C_CONVENTION);
            vtable.setPointer(2L * Native.POINTER_SIZE, getpid);
            dispatchObject.setPointer(0, vtable);
            setPointer(dispatchObject);
        }

        @Override
        public HRESULT QueryInterface(REFIID riid, PointerByReference ppvObject) {
            ppvObject.setValue(dispatchObject);
            return WinNT.S_OK;
        }

        @Override
        public int AddRef() {
            referenceCount++;
            return referenceCount;
        }

        @Override
        public int Release() {
            if (referenceCount > 0) {
                referenceCount--;
            }
            return referenceCount;
        }

        @Override
        public Pointer getPointer() {
            Pointer pointer = super.getPointer();
            if (pointer == null) {
                return Pointer.NULL;
            }
            return pointer;
        }
    }
}
