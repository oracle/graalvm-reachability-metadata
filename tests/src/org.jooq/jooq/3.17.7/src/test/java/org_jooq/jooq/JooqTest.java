/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.BuiltInDataType;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org_jooq.jooq.model.tables.records.CourseMaterialRecord;
import org_jooq.jooq.model.tables.records.CourseRecord;
import org_jooq.jooq.model.tables.records.StudentCourseRecord;
import org_jooq.jooq.model.tables.records.StudentRecord;
import org_jooq.jooq.model.tables.records.TeacherRecord;
import org_jooq.jooq.proxy.CourseProxy;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org_jooq.jooq.model.College.COLLEGE;
import static org_jooq.jooq.model.Tables.COURSE;
import static org_jooq.jooq.model.Tables.COURSE_MATERIAL;
import static org_jooq.jooq.model.Tables.STUDENT;
import static org_jooq.jooq.model.Tables.STUDENT_COURSE;
import static org_jooq.jooq.model.Tables.TEACHER;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JooqTest {

    private static final String USERNAME = "fred";

    private static final String PASSWORD = "secret";

    private static final String JDBC_URL = "jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    private final Map<String, Long> studentIds = new HashMap<>();

    private final Map<String, Long> teacherIds = new HashMap<>();

    private final Map<String, Long> courseIds = new HashMap<>();

    private Connection conn;

    private DSLContext context;

    @BeforeAll
    public void init() throws Exception {
        Properties props = new Properties();
        props.setProperty("user", USERNAME);
        props.setProperty("password", PASSWORD);
        conn = DriverManager.getConnection(JDBC_URL, props);
        context = DSL.using(conn, SQLDialect.H2);
        createSchema();
        insertTestData();
    }

    @AfterAll
    public void close() throws Exception {
        deleteTestData();
        dropSchema();
        conn.close();
    }

    @Test
    public void testLoadStudents() {
        List<StudentRecord> students = context.select()
                .from(STUDENT)
                .orderBy(STUDENT.AGE.asc())
                .fetch()
                .into(StudentRecord.class);

        assertThat(students)
                .hasSize(5)
                .extracting(StudentRecord::getId)
                .containsExactly(studentIds.get("John Miller"), studentIds.get("Patricia Miller"), studentIds.get("Robert Smith"),
                        studentIds.get("John Smith"), studentIds.get("Mary Smith"));

        students = context.select()
                .from(STUDENT)
                .where(STUDENT.LAST_NAME.eq("Smith"))
                .and(STUDENT.AGE.ge(25))
                .fetch()
                .into(StudentRecord.class);

        assertThat(students)
                .hasSize(2)
                .extracting(StudentRecord::getId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Mary Smith"));

        students = context.select()
                .from(STUDENT)
                .join(STUDENT_COURSE).on(STUDENT_COURSE.STUDENT_ID.eq(STUDENT.ID))
                .join(COURSE).on(COURSE.ID.eq(STUDENT_COURSE.COURSE_ID))
                .where(COURSE.TITLE.eq("Statistics"))
                .fetch()
                .into(StudentRecord.class);

        assertThat(students)
                .hasSize(3)
                .extracting(StudentRecord::getId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Robert Smith"), studentIds.get("Mary Smith"));

        StudentRecord student = context.select()
                .from(STUDENT)
                .where(STUDENT.ID.eq(studentIds.get("John Smith")))
                .fetchSingle()
                .into(StudentRecord.class);

        assertThat(student).isNotNull();
        assertThat(student.getFirstName()).isEqualTo("John");
        assertThat(student.getLastName()).isEqualTo("Smith");
        assertThat(student.getGender()).isEqualTo("male");
        assertThat(student.getAge()).isEqualTo(25);
    }

    @Test
    public void testLoadTeacher() {
        TeacherRecord teacher = context.select()
                .from(TEACHER)
                .where(TEACHER.ID.eq(teacherIds.get("Jennifer Brown")))
                .fetchSingle()
                .into(TeacherRecord.class);

        assertThat(teacher).isNotNull();
        assertThat(teacher.getFirstName()).isEqualTo("Jennifer");
        assertThat(teacher.getLastName()).isEqualTo("Brown");
    }

    @Test
    public void testLoadCourse() {
        CourseRecord course = context.select()
                .from(COURSE)
                .where(COURSE.ID.eq(courseIds.get("Statistics")))
                .fetchSingle()
                .into(CourseRecord.class);

        List<StudentCourseRecord> studentCourses = context.select()
                .from(STUDENT_COURSE)
                .where(STUDENT_COURSE.COURSE_ID.eq(courseIds.get("Statistics")))
                .fetch()
                .into(StudentCourseRecord.class);

        assertThat(course).isNotNull();
        assertThat(course.getTitle()).isEqualTo("Statistics");
        assertThat(course.getTeacherId()).isEqualTo(teacherIds.get("Jennifer Brown"));
        assertThat(studentCourses)
                .hasSize(3)
                .extracting(StudentCourseRecord::getStudentId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Robert Smith"), studentIds.get("Mary Smith"));
    }

    @Test
    public void testArrayDataTypes() throws Exception {
        Set<Class> types = new HashSet<>();

        for (Field field : SQLDataType.class.getFields()) {
            Object value = field.get(null);
            if (value instanceof BuiltInDataType) {
                Class type = ((BuiltInDataType) value).getType();
                if (!type.isArray()) {
                    types.add(type);
                }
            }
        }

        for (Class type : types) {
            assertThat(Class.forName(type.arrayType().getName())).isNotNull();
        }
    }

    @Test
    public void testProxy() {
        CourseProxy courseProxy = context.select()
                .from(COURSE)
                .where(COURSE.ID.eq(courseIds.get("Design")))
                .fetchSingle()
                .into(CourseProxy.class);

        assertThat(courseProxy).isNotNull();
        assertThat(courseProxy.getTitle()).isEqualTo("Design");
        assertThat(courseProxy.getTeacherId()).isEqualTo(teacherIds.get("Richard Davis"));
    }

    private void createSchema() {
        context.createSchema(COLLEGE).execute();

        context.createTable(STUDENT)
                .columns(STUDENT.fields())
                .primaryKey(STUDENT.ID)
                .execute();

        context.createTable(TEACHER)
                .columns(TEACHER.fields())
                .primaryKey(TEACHER.ID)
                .execute();

        context.createTable(COURSE)
                .columns(COURSE.fields())
                .primaryKey(COURSE.ID)
                .constraint(DSL.constraint("FK_COURSE_TEACHER_ID").foreignKey(COURSE.TEACHER_ID).references(TEACHER, TEACHER.ID))
                .execute();

        context.createTable(COURSE_MATERIAL)
                .columns(COURSE_MATERIAL.fields())
                .primaryKey(COURSE_MATERIAL.ID)
                .constraint(DSL.constraint("FK_COURSE_MATERIAL_COURSE_ID").foreignKey(COURSE_MATERIAL.COURSE_ID).references(COURSE, COURSE.ID))
                .unique(COURSE_MATERIAL.COURSE_ID)
                .execute();

        context.createTable(STUDENT_COURSE)
                .columns(STUDENT_COURSE.fields())
                .constraint(DSL.constraint("FK_STUDENT_COURSE_COURSE_ID").foreignKey(STUDENT_COURSE.STUDENT_ID).references(STUDENT, STUDENT.ID))
                .constraint(DSL.constraint("FK_STUDENT_COURSE_STUDENT_ID").foreignKey(STUDENT_COURSE.COURSE_ID).references(COURSE, COURSE.ID))
                .execute();
    }

    private void dropSchema() {
        context.dropTable(STUDENT_COURSE).execute();
        context.dropTable(COURSE_MATERIAL).execute();
        context.dropTable(COURSE).execute();
        context.dropTable(TEACHER).execute();
        context.dropTable(STUDENT).execute();
        context.dropSchema(COLLEGE).execute();
    }

    private void insertTestData() {
        StudentRecord student1 = createStudentRecord("John", "Smith", "male", 25);
        StudentRecord student2 = createStudentRecord("Robert", "Smith", "male", 24);
        StudentRecord student3 = createStudentRecord("John", "Miller", "male", 21);
        StudentRecord student4 = createStudentRecord("Mary", "Smith", "female", 26);
        StudentRecord student5 = createStudentRecord("Patricia", "Miller", "female", 22);

        TeacherRecord teacher1 = createTeacherRecord("Jennifer", "Brown");
        TeacherRecord teacher2 = createTeacherRecord("Richard", "Davis");

        CourseRecord course1 = createCourseRecord("Statistics", teacher1.getId());
        CourseRecord course2 = createCourseRecord("Math", teacher1.getId());
        CourseRecord course3 = createCourseRecord("Design", teacher2.getId());

        createStudentCourseRecord(course1.getId(), student1.getId());
        createStudentCourseRecord(course1.getId(), student2.getId());
        createStudentCourseRecord(course1.getId(), student4.getId());
        createStudentCourseRecord(course2.getId(), student1.getId());
        createStudentCourseRecord(course2.getId(), student2.getId());
        createStudentCourseRecord(course2.getId(), student4.getId());
        createStudentCourseRecord(course2.getId(), student5.getId());
        createStudentCourseRecord(course3.getId(), student3.getId());
        createStudentCourseRecord(course3.getId(), student4.getId());
        createStudentCourseRecord(course3.getId(), student5.getId());
    }

    private void deleteTestData() {
        context.delete(STUDENT_COURSE).execute();
        context.delete(COURSE_MATERIAL).execute();
        context.delete(COURSE).execute();
        context.delete(TEACHER).execute();
        context.delete(STUDENT).execute();
    }

    private StudentRecord createStudentRecord(String firstName, String lastName, String gender, int age) {
        StudentRecord student = context.newRecord(STUDENT);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setGender(gender);
        student.setAge(age);
        student.setBirthDate(LocalDate.now());
        student.store();
        studentIds.put(firstName + " " + lastName, student.getId());
        return student;
    }

    private TeacherRecord createTeacherRecord(String firstName, String lastName) {
        TeacherRecord teacher = context.newRecord(TEACHER);
        teacher.setFirstName(firstName);
        teacher.setLastName(lastName);
        teacher.store();
        teacherIds.put(firstName + " " + lastName, teacher.getId());
        return teacher;
    }

    private CourseRecord createCourseRecord(String title, Long teacherId) {
        CourseRecord course = context.newRecord(COURSE);
        course.setTitle(title);
        course.setTeacherId(teacherId);
        course.store();
        courseIds.put(title, course.getId());

        CourseMaterialRecord courseMaterial = context.newRecord(COURSE_MATERIAL);
        courseMaterial.setUrl(title + "Url");
        courseMaterial.setCourseId(course.getId());
        courseMaterial.store();
        return course;
    }

    private StudentCourseRecord createStudentCourseRecord(Long courseId, Long studentId) {
        StudentCourseRecord studentCourse = context.newRecord(STUDENT_COURSE);
        studentCourse.setCourseId(courseId);
        studentCourse.setStudentId(studentId);
        studentCourse.insert();
        return studentCourse;
    }
}
