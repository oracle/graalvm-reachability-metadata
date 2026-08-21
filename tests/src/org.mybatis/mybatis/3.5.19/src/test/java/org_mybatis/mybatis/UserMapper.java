/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

interface UserMapper {
    @Insert("INSERT INTO users (id, name) VALUES (#{id}, #{name})")
    void insertUser(@Param("id") int id, @Param("name") String name);

    @Select("SELECT name FROM users WHERE id = #{id}")
    String findUserName(@Param("id") int id);
}
