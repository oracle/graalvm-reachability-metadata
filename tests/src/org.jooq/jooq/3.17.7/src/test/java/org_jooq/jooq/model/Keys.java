/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq.model;


import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;

import org_jooq.jooq.model.tables.Course;
import org_jooq.jooq.model.tables.CourseMaterial;
import org_jooq.jooq.model.tables.Student;
import org_jooq.jooq.model.tables.StudentCourse;
import org_jooq.jooq.model.tables.Teacher;
import org_jooq.jooq.model.tables.records.CourseMaterialRecord;
import org_jooq.jooq.model.tables.records.CourseRecord;
import org_jooq.jooq.model.tables.records.StudentCourseRecord;
import org_jooq.jooq.model.tables.records.StudentRecord;
import org_jooq.jooq.model.tables.records.TeacherRecord;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * college.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<CourseRecord> KEY_COURSE_PRIMARY = Internal.createUniqueKey(Course.COURSE, DSL.name("KEY_course_PRIMARY"), new TableField[] { Course.COURSE.ID }, true);
    public static final UniqueKey<CourseMaterialRecord> KEY_COURSE_MATERIAL_COURSE_ID = Internal.createUniqueKey(CourseMaterial.COURSE_MATERIAL, DSL.name("KEY_course_material_course_id"), new TableField[] { CourseMaterial.COURSE_MATERIAL.COURSE_ID }, true);
    public static final UniqueKey<CourseMaterialRecord> KEY_COURSE_MATERIAL_PRIMARY = Internal.createUniqueKey(CourseMaterial.COURSE_MATERIAL, DSL.name("KEY_course_material_PRIMARY"), new TableField[] { CourseMaterial.COURSE_MATERIAL.ID }, true);
    public static final UniqueKey<StudentRecord> KEY_STUDENT_PRIMARY = Internal.createUniqueKey(Student.STUDENT, DSL.name("KEY_student_PRIMARY"), new TableField[] { Student.STUDENT.ID }, true);
    public static final UniqueKey<TeacherRecord> KEY_TEACHER_PRIMARY = Internal.createUniqueKey(Teacher.TEACHER, DSL.name("KEY_teacher_PRIMARY"), new TableField[] { Teacher.TEACHER.ID }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<CourseRecord, TeacherRecord> FK_COURSE_TEACHER_ID = Internal.createForeignKey(Course.COURSE, DSL.name("FK_COURSE_TEACHER_ID"), new TableField[] { Course.COURSE.TEACHER_ID }, Keys.KEY_TEACHER_PRIMARY, new TableField[] { Teacher.TEACHER.ID }, true);
    public static final ForeignKey<CourseMaterialRecord, CourseRecord> FK_COURSE_MATERIAL_COURSE_ID = Internal.createForeignKey(CourseMaterial.COURSE_MATERIAL, DSL.name("FK_COURSE_MATERIAL_COURSE_ID"), new TableField[] { CourseMaterial.COURSE_MATERIAL.COURSE_ID }, Keys.KEY_COURSE_PRIMARY, new TableField[] { Course.COURSE.ID }, true);
    public static final ForeignKey<StudentCourseRecord, StudentRecord> FK_STUDENT_COURSE_COURSE_ID = Internal.createForeignKey(StudentCourse.STUDENT_COURSE, DSL.name("FK_STUDENT_COURSE_COURSE_ID"), new TableField[] { StudentCourse.STUDENT_COURSE.STUDENT_ID }, Keys.KEY_STUDENT_PRIMARY, new TableField[] { Student.STUDENT.ID }, true);
    public static final ForeignKey<StudentCourseRecord, CourseRecord> FK_STUDENT_COURSE_STUDENT_ID = Internal.createForeignKey(StudentCourse.STUDENT_COURSE, DSL.name("FK_STUDENT_COURSE_STUDENT_ID"), new TableField[] { StudentCourse.STUDENT_COURSE.COURSE_ID }, Keys.KEY_COURSE_PRIMARY, new TableField[] { Course.COURSE.ID }, true);
}
