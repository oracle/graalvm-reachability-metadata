/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.COM.tlb.imp.TlbBase;
import com.sun.jna.platform.win32.COM.tlb.imp.TlbConst;
import org.junit.jupiter.api.Test;

public class TlbBaseTest {

    private static final String ENUM_TEMPLATE_RESOURCE = "com/sun/jna/platform/win32/COM/tlb/imp/TlbEnum.template";

    @Test
    void constructorLoadsTemplateResourceThroughClassLoader() {
        TemplateBackedTlbBase tlbBase = new TemplateBackedTlbBase(TlbConst.BINDING_MODE_VTABLE);

        assertThat(tlbBase.getClassBuffer().toString())
            .contains("package ${packagename};")
            .contains("public class ${classname} extends Structure")
            .contains("${content}");
        assertThat(tlbBase.usesVTableBinding()).isTrue();
    }

    private static final class TemplateBackedTlbBase extends TlbBase {

        private TemplateBackedTlbBase(String bindingMode) {
            super(0, null, null, bindingMode);
        }

        @Override
        protected String getClassTemplate() {
            return ENUM_TEMPLATE_RESOURCE;
        }

        private boolean usesVTableBinding() {
            return isVTableMode();
        }

    }
}
