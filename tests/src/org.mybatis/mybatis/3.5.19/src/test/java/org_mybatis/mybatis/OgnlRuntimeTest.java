/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OgnlRuntimeTest {
    @Test
    void deletesEntitiesUsingMetaObjectInForeach() throws Exception {
        try (SqlSession session = TestSupport.factory(DeleteMapper.class).openSession()) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TABLE entities (id INTEGER PRIMARY KEY)");
                statement.execute("INSERT INTO entities VALUES (1), (2), (3)");
            }
            assertEquals(2, session.getMapper(DeleteMapper.class).deleteByIds(List.of(new Entity(1), new Entity(3))));
            try (Statement statement = session.getConnection().createStatement();
                    ResultSet rows = statement.executeQuery("SELECT id FROM entities")) {
                rows.next();
                assertEquals(2, rows.getInt(1));
            }
        }
    }

    @Test
    void evaluatesFieldsConstructorsAndVarargs() {
        Entity entity = new Entity(4);
        assertEquals(4, OgnlCache.getValue("id", entity));
        assertEquals(9, OgnlCache.getValue("id = 9", entity));
        assertEquals(9, entity.id);
        assertEquals("entity", OgnlCache.getValue("@org_mybatis.mybatis.OgnlRuntimeTest$Entity@KIND", entity));
        assertEquals(7, OgnlCache.getValue("new org_mybatis.mybatis.OgnlRuntimeTest$Entity(7).id", entity));
        assertEquals("alpha", OgnlCache.getValue("join('alpha')", entity));
        assertEquals("alpha,beta", OgnlCache.getValue("join('alpha', 'beta')", entity));
        assertEquals("id", OgnlCache.getValue(
                "@org.apache.ibatis.reflection.SystemMetaObject@forObject(item).findProperty('ID', false)",
                Map.of("item", entity)));
    }

    public interface DeleteMapper {
        @Delete("""
                <script>
                DELETE FROM entities WHERE id IN
                <foreach collection="items" item="item" open="(" separator="," close=")">
                  <choose>
                    <when test="item != null and @org.apache.ibatis.reflection.SystemMetaObject@forObject(item).findProperty('id', false) != null">
                      #{item.id}
                    </when>
                    <otherwise>#{item}</otherwise>
                  </choose>
                </foreach>
                </script>
                """)
        int deleteByIds(@Param("items") List<Entity> items);
    }

    public static class Entity {
        public static final String KIND = "entity";
        public int id;

        public Entity(int id) {
            this.id = id;
        }

        public String join(String... values) {
            return String.join(",", values);
        }
    }
}
