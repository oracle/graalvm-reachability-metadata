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
import java.util.AbstractMap;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class JPackageTest {
    @Test
    void refResolvesClassBySimpleNameWithinPackage() throws ClassNotFoundException {
        JCodeModel codeModel = new JCodeModel();
        JPackage javaUtilPackage = codeModel._package(HashMap.class.getPackageName());

        JClass referencedClass = javaUtilPackage.ref(HashMap.class.getSimpleName());

        assertThat(referencedClass.fullName()).isEqualTo(HashMap.class.getName());
        assertThat(referencedClass.name()).isEqualTo(HashMap.class.getSimpleName());
        assertThat(referencedClass._extends().fullName()).isEqualTo(AbstractMap.class.getName());
    }
}
