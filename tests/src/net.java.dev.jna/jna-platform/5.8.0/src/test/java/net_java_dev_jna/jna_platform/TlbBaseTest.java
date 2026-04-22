/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.COM.tlb.imp.TlbBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TlbBaseTest {
    private static final String TEMPLATE_RESOURCE = "com/sun/jna/platform/win32/COM/tlb/imp/TlbCoClass.template";

    @Test
    void constructorLoadsTemplateResourceFromTheLibraryClasspath() {
        TestTlbBase tlbBase = new TestTlbBase();
        String renderedTemplate = tlbBase.getClassBuffer().toString();

        assertThat(tlbBase.getClassBuffer()).isNotNull();
        assertThat(renderedTemplate)
                .contains("package ${packagename};")
                .contains("public class ${classname} extends COMEarlyBindingObject")
                .contains("${content}");
    }

    @Test
    void namingHelpersAndContentReplacementUpdateTheLoadedTemplate() {
        TestTlbBase tlbBase = new TestTlbBase();

        tlbBase.applyPackageName("example.generated");
        tlbBase.applyClassName("SampleComType");
        tlbBase.applyContent("    public String marker() {\n        return \"ok\";\n    }");
        tlbBase.setFilename("SampleComType");
        tlbBase.setName("Sample Type");

        String renderedTemplate = tlbBase.getClassBuffer().toString();
        assertThat(renderedTemplate)
                .contains("package example.generated;")
                .contains("public class SampleComType extends COMEarlyBindingObject")
                .contains("public String marker() {")
                .contains("return \"ok\";");
        assertThat(tlbBase.getFilename()).isEqualTo("SampleComType.java");
        assertThat(tlbBase.getName()).isEqualTo("Sample Type");
    }

    @Test
    void reservedMethodDetectionAndBindingModeChecksAreCaseInsensitive() {
        TestTlbBase dispIdBase = new TestTlbBase();
        TestTlbBase vtableBase = new TestTlbBase("VTaBlE");

        assertThat(dispIdBase.isDispIdBindingMode()).isTrue();
        assertThat(dispIdBase.isVTableBindingMode()).isFalse();
        assertThat(vtableBase.isVTableBindingMode()).isTrue();
        assertThat(vtableBase.isDispIdBindingMode()).isFalse();
        assertThat(vtableBase.isReservedComMethod("release")).isTrue();
        assertThat(vtableBase.isReservedComMethod("GetTypeInfo")).isTrue();
        assertThat(vtableBase.isReservedComMethod("CustomMethod")).isFalse();
    }

    private static final class TestTlbBase extends TlbBase {
        private TestTlbBase() {
            super(0, null, null);
        }

        private TestTlbBase(String bindingMode) {
            super(0, null, null, bindingMode);
        }

        @Override
        protected String getClassTemplate() {
            return TEMPLATE_RESOURCE;
        }

        private void applyPackageName(String packageName) {
            createPackageName(packageName);
        }

        private void applyClassName(String className) {
            createClassName(className);
        }

        private void applyContent(String content) {
            createContent(content);
        }

        private boolean isReservedComMethod(String methodName) {
            return isReservedMethod(methodName);
        }

        private boolean isVTableBindingMode() {
            return isVTableMode();
        }

        private boolean isDispIdBindingMode() {
            return isDispIdMode();
        }
    }
}
