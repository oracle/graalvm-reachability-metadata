/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq.proxy;

public interface CourseProxy {

    void setId(Long id);

    Long getId();

    void setTitle(String title);

    String getTitle();

    void setTeacherId(Long teacherId);

    Long getTeacherId();
}
