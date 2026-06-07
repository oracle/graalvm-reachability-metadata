/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaConstantInnerMethodHandleInnerDispatcherInnerForJava7CapableVmTest {
    @Test
    void revealsMethodHandleInfoUsingConfiguredConstructor() throws Exception {
        DispatcherBridge bridge = DispatcherBridge.create();
        Object methodHandle = new Object();

        SampleMethodHandleInfo methodHandleInfo = bridge.reveal(methodHandle);
        SampleMethodType methodType = (SampleMethodType) bridge.getMethodType(methodHandleInfo);

        assertThat(methodHandleInfo.getMethodHandle()).isSameAs(methodHandle);
        assertThat(bridge.getReferenceKind(methodHandleInfo))
                .isEqualTo(JavaConstant.MethodHandle.HandleType.INVOKE_STATIC.getIdentifier());
        assertThat(bridge.getDeclaringClass(methodHandleInfo)).isEqualTo(SampleTarget.class);
        assertThat(bridge.getName(methodHandleInfo)).isEqualTo("sample");
        assertThat(bridge.returnType(methodType)).isEqualTo(String.class);
        assertThat(bridge.parameterArray(methodType)).containsExactly(int.class, long.class);
        assertThat(bridge.dispatcherLookupType(bridge.publicLookup())).isEqualTo(SampleTarget.class);
    }

    private static class DispatcherBridge extends JavaConstant.MethodHandle {
        private final Java7Dispatcher dispatcher;

        DispatcherBridge(Java7Dispatcher dispatcher) {
            super(HandleType.INVOKE_STATIC,
                    TypeDescription.ForLoadedType.of(SampleTarget.class),
                    "sample",
                    TypeDescription.ForLoadedType.of(String.class),
                    Collections.<TypeDescription>emptyList());
            this.dispatcher = dispatcher;
            this.dispatcher.initialize();
        }

        static DispatcherBridge create() throws Exception {
            return new DispatcherBridge(new Java7Dispatcher(
                    SampleMethodHandles.class.getMethod("publicLookup"),
                    SampleMethodHandleInfo.class.getMethod("getName"),
                    SampleMethodHandleInfo.class.getMethod("getDeclaringClass"),
                    SampleMethodHandleInfo.class.getMethod("getReferenceKind"),
                    SampleMethodHandleInfo.class.getMethod("getMethodType"),
                    SampleMethodType.class.getMethod("returnType"),
                    SampleMethodType.class.getMethod("parameterArray"),
                    SampleLookup.class.getMethod("lookupClass"),
                    SampleMethodHandleInfo.class.getConstructor(Object.class)));
        }

        Object publicLookup() {
            return dispatcher.publicLookup();
        }

        Class<?> dispatcherLookupType(Object lookup) {
            return dispatcher.lookupType(lookup);
        }

        SampleMethodHandleInfo reveal(Object methodHandle) {
            return (SampleMethodHandleInfo) dispatcher.reveal(publicLookup(), methodHandle);
        }

        Object getMethodType(Object methodHandleInfo) {
            return dispatcher.getMethodType(methodHandleInfo);
        }

        int getReferenceKind(Object methodHandleInfo) {
            return dispatcher.getReferenceKind(methodHandleInfo);
        }

        Class<?> getDeclaringClass(Object methodHandleInfo) {
            return dispatcher.getDeclaringClass(methodHandleInfo);
        }

        String getName(Object methodHandleInfo) {
            return dispatcher.getName(methodHandleInfo);
        }

        Class<?> returnType(Object methodType) {
            return dispatcher.returnType(methodType);
        }

        Object[] parameterArray(Object methodType) {
            return dispatcher.parameterArray(methodType).toArray();
        }

        private static class Java7Dispatcher extends Dispatcher.ForJava7CapableVm {
            Java7Dispatcher(Method publicLookup,
                            Method getName,
                            Method getDeclaringClass,
                            Method getReferenceKind,
                            Method getMethodType,
                            Method returnType,
                            Method parameterArray,
                            Method lookupClass,
                            Constructor<?> methodInfo) {
                super(publicLookup,
                        getName,
                        getDeclaringClass,
                        getReferenceKind,
                        getMethodType,
                        returnType,
                        parameterArray,
                        lookupClass,
                        methodInfo);
            }
        }
    }

    public static class SampleMethodHandles {
        public static SampleLookup publicLookup() {
            return new SampleLookup();
        }
    }

    public static class SampleLookup {
        public Class<?> lookupClass() {
            return SampleTarget.class;
        }
    }

    public static class SampleMethodHandleInfo {
        private final Object methodHandle;

        public SampleMethodHandleInfo(Object methodHandle) {
            this.methodHandle = methodHandle;
        }

        Object getMethodHandle() {
            return methodHandle;
        }

        public SampleMethodType getMethodType() {
            return new SampleMethodType();
        }

        public int getReferenceKind() {
            return JavaConstant.MethodHandle.HandleType.INVOKE_STATIC.getIdentifier();
        }

        public Class<?> getDeclaringClass() {
            return SampleTarget.class;
        }

        public String getName() {
            return "sample";
        }
    }

    public static class SampleMethodType {
        public Class<?> returnType() {
            return String.class;
        }

        public Class<?>[] parameterArray() {
            return new Class<?>[] {int.class, long.class};
        }
    }

    public static class SampleTarget {
        public static String sample(int first, long second) {
            return first + ":" + second;
        }
    }
}
