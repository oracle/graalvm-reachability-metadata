/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlSessionManagerInnerSqlSessionInterceptorTest {
    @Test
    void delegatesToManagedAndAutomaticSessions() {
        SqlSessionManager manager = SqlSessionManager.newInstance(TestSupport.factory(Mapper.class));
        assertEquals(19, manager.getMapper(Mapper.class).number());
        manager.startManagedSession();
        try {
            assertTrue(manager.isManagedSessionStarted());
            assertEquals(19, manager.getMapper(Mapper.class).number());
            manager.commit();
        } finally {
            manager.close();
        }
        assertFalse(manager.isManagedSessionStarted());
    }

    public interface Mapper {
        @Select("SELECT 19")
        int number();
    }
}
