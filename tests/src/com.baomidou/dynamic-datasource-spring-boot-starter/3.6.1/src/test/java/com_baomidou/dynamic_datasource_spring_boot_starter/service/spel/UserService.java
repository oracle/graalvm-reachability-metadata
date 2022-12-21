/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.dynamic_datasource_spring_boot_starter.service.spel;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "UnusedReturnValue", "unused"})
@Service
@DS("slave")
public class UserService {

    private final DataSource dataSource;

    public UserService(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @DS("#session.tenantName")
    public List<User> selectSpelBySession() {
        List<User> result = new LinkedList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select * from t_user");
            while (resultSet.next()) {
                result.add(new User(resultSet.getInt(1), resultSet.getString(2), resultSet.getInt(3), resultSet.getString(4)));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @DS("#header.tenantName")
    public List<User> selectSpelByHeader() {
        List<User> result = new LinkedList<>();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("select * from t_user");
            while (resultSet.next()) {
                result.add(new User(resultSet.getInt(1), resultSet.getString(2), resultSet.getInt(3), resultSet.getString(4)));
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @DS("#tenantName")
    public String selectSpelByKey(String tenantName) {
        return DynamicDataSourceContextHolder.peek();

    }

    @DS("#user.tenantName")
    public String selecSpelByTenant(User user) {
        return DynamicDataSourceContextHolder.peek();
    }
}
