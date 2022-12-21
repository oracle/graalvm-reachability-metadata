/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_spring_boot_starter.service.nest;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
@Service
@DS("student")
public class StudentService {
    private final DataSource dataSource;

    public StudentService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional
    public int addStudentWithTx(String name, Integer age) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement("insert into student (name,age) values (?,?)")) {
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, age);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int addStudentNoTx(String name, Integer age) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("insert into student (name,age) values (?,?)")) {
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, age);
            return preparedStatement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Student> selectStudents() {
        List<Student> result = new LinkedList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("SELECT * FROM student");
            while (resultSet.next()) {
                result.add(new Student(resultSet.getInt(1), resultSet.getString(2), resultSet.getInt(3)));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
