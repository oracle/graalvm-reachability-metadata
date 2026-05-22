/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JPackageTest {
    @Test
    void resolvesClassReferenceWithinPackage() throws ClassNotFoundException {
        JCodeModel codeModel = new JCodeModel();
        JPackage javaLangPackage = codeModel._package("java.lang");

        JClass stringClass = javaLangPackage.ref("String");

        assertThat(stringClass.fullName()).isEqualTo(String.class.getName());
        assertThat(stringClass.name()).isEqualTo(String.class.getSimpleName());
    }
}
