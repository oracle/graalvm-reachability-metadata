/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.apache.groovy.runtime.ObjectUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ObjectUtilTest {
    @Test
    void clonesAnObjectThroughItsPublicCloneMethod() throws Throwable {
        Document original = new Document("draft");
        Document copy = ObjectUtil.cloneObject(original);
        assertNotSame(original, copy);
        assertEquals("draft", copy.title);
    }

    public static class Document implements Cloneable {
        public final String title;

        public Document(String title) {
            this.title = title;
        }

        @Override
        public Document clone() throws CloneNotSupportedException {
            return (Document) super.clone();
        }
    }
}
