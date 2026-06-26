/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.COM.tlb.imp.TlbBase;

public class TlbBaseTest {
    @Test
    void constructorLoadsClasspathTemplateIntoClassBuffer() {
        TemplateBackedTlbBase tlbBase = new TemplateBackedTlbBase();

        assertThat(tlbBase.getClassBuffer()).isNotNull();
        assertThat(tlbBase.getClassBuffer().toString())
                .contains("package ${packagename};")
                .contains("public class ${classname} extends Structure")
                .contains("${content}");
    }

    @Test
    void templateContentCanBeSpecializedAfterLoading() {
        TemplateBackedTlbBase tlbBase = new TemplateBackedTlbBase();

        tlbBase.createContent("public static final int ANSWER = 42;");

        assertThat(tlbBase.getClassBuffer().toString())
                .contains("public static final int ANSWER = 42;")
                .doesNotContain("${content}");
    }

    private static final class TemplateBackedTlbBase extends TlbBase {
        private TemplateBackedTlbBase() {
            super(0, null, null);
        }

        @Override
        protected String getClassTemplate() {
            return "com/sun/jna/platform/win32/COM/tlb/imp/TlbEnum.template";
        }
    }
}
