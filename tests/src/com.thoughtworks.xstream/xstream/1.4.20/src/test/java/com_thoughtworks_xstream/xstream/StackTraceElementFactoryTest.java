/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.converters.extended.StackTraceElementFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class StackTraceElementFactoryTest {
    @Test
    void createsSourceLineStackTraceElement() {
        StackTraceElementFactory factory = new StackTraceElementFactory();

        StackTraceElement element = factory.element("com.example.Worker", "process", "Worker.java", 42);

        assertThat(element.getClassName()).isEqualTo("com.example.Worker");
        assertThat(element.getMethodName()).isEqualTo("process");
        assertThat(element.getFileName()).isEqualTo("Worker.java");
        assertThat(element.getLineNumber()).isEqualTo(42);
        assertThat(element.isNativeMethod()).isFalse();
    }

    @Test
    void createsSpecialStackTraceElementLocations() {
        StackTraceElementFactory factory = new StackTraceElementFactory();

        StackTraceElement unknownSource = factory.unknownSourceElement("com.example.Worker", "lookup");
        StackTraceElement nativeMethod = factory.nativeMethodElement("com.example.NativeWorker", "invoke");

        assertThat(unknownSource.getClassName()).isEqualTo("com.example.Worker");
        assertThat(unknownSource.getMethodName()).isEqualTo("lookup");
        assertThat(unknownSource.getFileName()).isEqualTo("Unknown Source");
        assertThat(unknownSource.getLineNumber()).isEqualTo(-1);
        assertThat(unknownSource.isNativeMethod()).isFalse();

        assertThat(nativeMethod.getClassName()).isEqualTo("com.example.NativeWorker");
        assertThat(nativeMethod.getMethodName()).isEqualTo("invoke");
        assertThat(nativeMethod.getFileName()).isEqualTo("Native Method");
        assertThat(nativeMethod.getLineNumber()).isEqualTo(-2);
        assertThat(nativeMethod.isNativeMethod()).isTrue();
    }
}
