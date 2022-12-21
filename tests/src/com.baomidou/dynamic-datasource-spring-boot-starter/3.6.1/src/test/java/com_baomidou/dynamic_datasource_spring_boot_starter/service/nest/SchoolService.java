/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_spring_boot_starter.service.nest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolService {
    private final TeacherService teacherService;
    private final StudentService studentService;

    public SchoolService(TeacherService teacherService, StudentService studentService) {
        this.teacherService = teacherService;
        this.studentService = studentService;
    }

    @Transactional
    public int addTeacherAndStudentWithTx() {
        int aa = teacherService.addTeacherNoTx("aa", 3);
        int bb = studentService.addStudentNoTx("bb", 4);
        return aa + bb;
    }
}
