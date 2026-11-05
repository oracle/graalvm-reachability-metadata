/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.apache.groovy.runtime.ObjectUtil;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ObjectUtilInnerMethodHandleHolderTest {
    @Test
    void clonesThroughTheLibrarySuppliedMethodHandle() throws Throwable {
        ObjectUtilTest.Document original = new ObjectUtilTest.Document("published");
        MethodHandle cloner = ObjectUtil.getCloneObjectMethodHandle();
        ObjectUtilTest.Document copy = (ObjectUtilTest.Document) cloner.invokeWithArguments(original);
        assertNotSame(original, copy);
        assertEquals("published", copy.title);
    }
}
