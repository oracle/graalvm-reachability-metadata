/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq.model;


import org_jooq.jooq.model.tables.Course;
import org_jooq.jooq.model.tables.CourseMaterial;
import org_jooq.jooq.model.tables.Student;
import org_jooq.jooq.model.tables.StudentCourse;
import org_jooq.jooq.model.tables.Teacher;


/**
 * Convenience access to all tables in college.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {

    /**
     * The table <code>college.course</code>.
     */
    public static final Course COURSE = Course.COURSE;

    /**
     * The table <code>college.course_material</code>.
     */
    public static final CourseMaterial COURSE_MATERIAL = CourseMaterial.COURSE_MATERIAL;

    /**
     * The table <code>college.student</code>.
     */
    public static final Student STUDENT = Student.STUDENT;

    /**
     * The table <code>college.student_course</code>.
     */
    public static final StudentCourse STUDENT_COURSE = StudentCourse.STUDENT_COURSE;

    /**
     * The table <code>college.teacher</code>.
     */
    public static final Teacher TEACHER = Teacher.TEACHER;
}
