/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class JPackageTest {
    @Test
    void refLoadsClassFromPackageName() throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JPackage javaLangPackage = codeModel._package("java.lang");

        try {
            JClass referencedClass = javaLangPackage.ref("String");

            assertThat(referencedClass.fullName()).isEqualTo("java.lang.String");
            assertThat(referencedClass.binaryName()).isEqualTo("java.lang.String");
            assertThat(referencedClass._package().name()).isEqualTo("java.lang");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
