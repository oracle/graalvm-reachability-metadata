/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org_hibernate_orm.hibernate_core.entity.Course;
import org_hibernate_orm.hibernate_core.entity.CourseMaterial;
import org_hibernate_orm.hibernate_core.entity.Gender;
import org_hibernate_orm.hibernate_core.entity.Student;
import org_hibernate_orm.hibernate_core.entity.Teacher;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractHibernateTest {

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    private Map<String, Long> studentIds = new HashMap<>();

    private Map<String, Long> teacherIds = new HashMap<>();

    private Map<String, Long> courseIds = new HashMap<>();

    @BeforeAll
    public void init() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", getJdbcUrl());
        properties.put("hibernate.dialect", getHibernateDialect());
        entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        entityManager = entityManagerFactory.createEntityManager();
        prepareTestData();
    }

    @AfterAll
    public void close() {
        entityManager.close();
        entityManagerFactory.close();
    }

    @AfterEach
    public void cleanup() {
        entityManager.clear();
    }

    protected abstract String getJdbcUrl();

    protected abstract String getHibernateDialect();

    @Test
    public void testLoadStudent() {
        Student student = entityManager.find(Student.class, studentIds.get("John Smith"));
        assertThat(student).isNotNull();
        assertThat(student.getFirstName()).isEqualTo("John");
        assertThat(student.getLastName()).isEqualTo("Smith");
        assertThat(student.getGender()).isEqualTo(Gender.MALE);
        assertThat(student.getAge()).isEqualTo(25);
    }

    @Test
    public void testLoadTeacher() {
        Teacher teacher = entityManager.find(Teacher.class, teacherIds.get("Jennifer Brown"));
        assertThat(teacher).isNotNull();
        assertThat(teacher.getFirstName()).isEqualTo("Jennifer");
        assertThat(teacher.getLastName()).isEqualTo("Brown");
        assertThat(teacher.getCourses())
                .hasSize(2)
                .extracting(Course::getTitle)
                .containsExactly("Statistics", "Math");
    }

    @Test
    public void testLoadCourse() {
        Course course = entityManager.find(Course.class, courseIds.get("Statistics"));
        assertThat(course).isNotNull();
        assertThat(course.getTitle()).isEqualTo("Statistics");
        assertThat(course.getTeacher())
                .extracting(Teacher::getFirstName)
                .isEqualTo("Jennifer");
        assertThat(course.getStudents())
                .hasSize(3)
                .extracting(Student::getId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Robert Smith"), studentIds.get("Mary Smith"));
    }

    @Test
    public void testFindStudentsUsingHql() {
        List<Student> studentsResult1 = entityManager.createQuery(
                        "from Student where lastName = :lastName order by firstName",
                        Student.class
                )
                .setParameter("lastName", "Smith")
                .getResultList();

        assertThat(studentsResult1)
                .hasSize(3)
                .extracting(Student::getId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Mary Smith"), studentIds.get("Robert Smith"));

        List<Student> studentsResult2 = entityManager.createQuery(
                        "select s from Course as c inner join c.students as s where c.title = :courseTitle",
                        Student.class
                )
                .setParameter("courseTitle", "Math")
                .getResultList();

        assertThat(studentsResult2)
                .hasSize(4)
                .extracting(Student::getId)
                .containsExactly(studentIds.get("John Smith"), studentIds.get("Robert Smith"), studentIds.get("Mary Smith"), studentIds.get("Patricia Miller"));
    }

    @Test
    public void testFindCoursesUsingHql() {
        List<Course> coursesResult = entityManager.createQuery(
                        "from Course where teacher.firstName = :teacherFirstName",
                        Course.class)
                .setParameter("teacherFirstName", "Jennifer")
                .getResultList();

        assertThat(coursesResult)
                .hasSize(2)
                .extracting(Course::getId)
                .containsExactly(courseIds.get("Statistics"), courseIds.get("Math"));
    }

    private void prepareTestData() {
        entityManager.getTransaction().begin();

        Student student1 = createStudent("John", "Smith", Gender.MALE, 25);
        Student student2 = createStudent("Robert", "Smith", Gender.MALE, 24);
        Student student3 = createStudent("John", "Miller", Gender.MALE, 21);
        Student student4 = createStudent("Mary", "Smith", Gender.FEMALE, 26);
        Student student5 = createStudent("Patricia", "Miller", Gender.FEMALE, 21);

        Teacher teacher1 = createTeacher("Jennifer", "Brown");
        Teacher teacher2 = createTeacher("Richard", "Davis");

        createCourse("Statistics", teacher1, Arrays.asList(student1, student2, student4));
        createCourse("Math", teacher1, Arrays.asList(student1, student2, student4, student5));
        createCourse("Design", teacher2, Arrays.asList(student3, student4, student5));

        entityManager.getTransaction().commit();
    }

    private Student createStudent(String firstName, String lastName, Gender gender, int age) {
        Student student = new Student();
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setGender(gender);
        student.setAge(age);
        student.setBirthDate(new Date());
        entityManager.persist(student);
        studentIds.put(firstName + " " + lastName, student.getId());
        return student;
    }

    private Teacher createTeacher(String firstName, String lastName) {
        Teacher teacher = new Teacher();
        teacher.setFirstName(firstName);
        teacher.setLastName(lastName);
        entityManager.persist(teacher);
        teacherIds.put(firstName + " " + lastName, teacher.getId());
        return teacher;
    }

    private Course createCourse(String title, Teacher teacher, List<Student> students) {
        Course course = new Course();
        course.setTitle(title);
        course.setTeacher(teacher);
        course.setStudents(students);
        entityManager.persist(course);
        courseIds.put(title, course.getId());

        CourseMaterial courseMaterial = new CourseMaterial();
        courseMaterial.setUrl(title + "Url");
        courseMaterial.setCourse(course);
        entityManager.persist(courseMaterial);
        return course;
    }
}
