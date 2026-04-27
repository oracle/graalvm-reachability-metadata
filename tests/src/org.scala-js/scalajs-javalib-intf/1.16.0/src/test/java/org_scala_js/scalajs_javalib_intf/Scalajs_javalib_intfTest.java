/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_js.scalajs_javalib_intf;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.scalajs.javalibintf.TypedArrayBuffer;

public class Scalajs_javalib_intfTest {
    private final Object typedArrayHandle = new Object();

    @Test
    void stackTraceElementColumnFactoryIsJvmStub() {
        assertJvmStub("createWithColumnNumber",
                () -> org.scalajs.javalibintf.StackTraceElement.createWithColumnNumber(
                        "example.GeneratedClass", "generatedMethod", "GeneratedClass.scala", 42, 17));
    }

    @Test
    void stackTraceElementColumnReaderIsJvmStub() {
        StackTraceElement stackTraceElement = new StackTraceElement(
                "example.GeneratedClass", "generatedMethod", "GeneratedClass.scala", 42);

        assertJvmStub("getColumnNumber",
                () -> org.scalajs.javalibintf.StackTraceElement.getColumnNumber(stackTraceElement));
    }

    @Test
    void typedArrayWrapperMethodsAreJvmStubs() {
        assertJvmStub("wrapInt8Array", () -> TypedArrayBuffer.wrapInt8Array(typedArrayHandle));
        assertJvmStub("wrapUint16Array", () -> TypedArrayBuffer.wrapUint16Array(typedArrayHandle));
        assertJvmStub("wrapInt16Array", () -> TypedArrayBuffer.wrapInt16Array(typedArrayHandle));
        assertJvmStub("wrapInt32Array", () -> TypedArrayBuffer.wrapInt32Array(typedArrayHandle));
        assertJvmStub("wrapFloat32Array", () -> TypedArrayBuffer.wrapFloat32Array(typedArrayHandle));
        assertJvmStub("wrapFloat64Array", () -> TypedArrayBuffer.wrapFloat64Array(typedArrayHandle));
    }

    @Test
    void arrayBufferAccessorsAreJvmStubsForHeapAndDirectBuffers() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(8);
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        ByteBuffer readOnlyBuffer = directBuffer.asReadOnlyBuffer();

        assertArrayBufferAccessorsAreStubs(heapBuffer);
        assertArrayBufferAccessorsAreStubs(directBuffer);
        assertArrayBufferAccessorsAreStubs(readOnlyBuffer);
    }

    @Test
    void typedArrayQueriesAreJvmStubsForEverySupportedPrimitiveBufferKind() {
        assertTypedArrayAccessorsAreStubs(ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()));
        assertTypedArrayAccessorsAreStubs(CharBuffer.allocate(8));
        assertTypedArrayAccessorsAreStubs(ShortBuffer.allocate(8));
        assertTypedArrayAccessorsAreStubs(IntBuffer.allocate(8));
        assertTypedArrayAccessorsAreStubs(FloatBuffer.allocate(8));
        assertTypedArrayAccessorsAreStubs(DoubleBuffer.allocate(8));
        assertTypedArrayAccessorsAreStubs(LongBuffer.allocate(8));
    }

    @Test
    void arrayBufferAccessorsAreJvmStubsForDirectBufferViewsWithOffsets() {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        directBuffer.position(8);
        directBuffer.limit(24);

        ByteBuffer slicedBuffer = directBuffer.slice().order(ByteOrder.nativeOrder());
        IntBuffer intView = slicedBuffer.asIntBuffer();

        assertArrayBufferAccessorsAreStubs(slicedBuffer);
        assertArrayBufferAccessorsAreStubs(intView);
    }

    @Test
    void typedArrayQueriesAreJvmStubsForDirectBufferViewsWithOffsets() {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        directBuffer.position(8);
        directBuffer.limit(56);

        ByteBuffer slicedBuffer = directBuffer.slice().order(ByteOrder.nativeOrder());

        assertTypedArrayAccessorsAreStubs(slicedBuffer);
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asCharBuffer());
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asShortBuffer());
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asIntBuffer());
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asFloatBuffer());
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asDoubleBuffer());
        assertTypedArrayAccessorsAreStubs(slicedBuffer.asLongBuffer());
    }

    private static void assertArrayBufferAccessorsAreStubs(java.nio.Buffer buffer) {
        assertJvmStub("hasArrayBuffer", () -> TypedArrayBuffer.hasArrayBuffer(buffer));
        assertJvmStub("arrayBuffer", () -> TypedArrayBuffer.arrayBuffer(buffer));
        assertJvmStub("arrayBufferOffset", () -> TypedArrayBuffer.arrayBufferOffset(buffer));
        assertJvmStub("dataView", () -> TypedArrayBuffer.dataView(buffer));
    }

    private static void assertTypedArrayAccessorsAreStubs(java.nio.Buffer buffer) {
        assertJvmStub("hasTypedArray", () -> TypedArrayBuffer.hasTypedArray(buffer));
        assertJvmStub("typedArray", () -> TypedArrayBuffer.typedArray(buffer));
    }

    private static void assertJvmStub(String methodName, ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
                .as(methodName)
                .isInstanceOf(AssertionError.class)
                .hasMessage("stub");
    }
}
