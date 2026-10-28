/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassTest {
    @Test
    void resolvesIndexedCollectionElementTypesFromMethodsAndFields() {
        MetaClass meta = MetaClass.forClass(Group.class, new DefaultReflectorFactory());
        assertEquals(String.class, meta.getGetterType("members[0].name"));
        assertEquals(String.class, meta.getGetterType("others[0].name"));
        assertEquals("members", meta.findProperty("MEMBERS"));
    }

    public static class Group {
        public List<Member> others;

        public List<Member> getMembers() {
            return others;
        }
    }

    public static class Member {
        public String name;
    }
}
