/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq.model;


import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;

import org_jooq.jooq.model.tables.Course;
import org_jooq.jooq.model.tables.CourseMaterial;
import org_jooq.jooq.model.tables.Student;
import org_jooq.jooq.model.tables.StudentCourse;
import org_jooq.jooq.model.tables.Teacher;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class College extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>college</code>
     */
    public static final College COLLEGE = new College();

    /**
     * The table <code>college.course</code>.
     */
    public final Course COURSE = Course.COURSE;

    /**
     * The table <code>college.course_material</code>.
     */
    public final CourseMaterial COURSE_MATERIAL = CourseMaterial.COURSE_MATERIAL;

    /**
     * The table <code>college.student</code>.
     */
    public final Student STUDENT = Student.STUDENT;

    /**
     * The table <code>college.student_course</code>.
     */
    public final StudentCourse STUDENT_COURSE = StudentCourse.STUDENT_COURSE;

    /**
     * The table <code>college.teacher</code>.
     */
    public final Teacher TEACHER = Teacher.TEACHER;

    /**
     * No further instances allowed
     */
    private College() {
        super("college", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.asList(
            Course.COURSE,
            CourseMaterial.COURSE_MATERIAL,
            Student.STUDENT,
            StudentCourse.STUDENT_COURSE,
            Teacher.TEACHER
        );
    }
}
