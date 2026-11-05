/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.transform.stc.SignatureCodecVersion1;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignatureCodecVersion1Test {
    @Test
    void roundTripsGenericAndArrayTypeSignatures() {
        SignatureCodecVersion1 codec = new SignatureCodecVersion1(getClass().getClassLoader());
        ClassNode list = ClassHelper.make(List.class).getPlainNodeReference();
        list.setGenericsTypes(new GenericsType[]{new GenericsType(ClassHelper.make(String.class))});
        ClassNode restored = codec.decode(codec.encode(list));
        assertEquals(List.class, restored.getTypeClass());
        assertEquals(String.class, restored.getGenericsTypes()[0].getType().getTypeClass());
        assertEquals(String[].class, codec.decode(codec.encode(ClassHelper.make(String[].class))).getTypeClass());
    }
}
