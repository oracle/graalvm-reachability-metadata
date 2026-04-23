/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.TableNameParser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectFactory;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.SqlParserUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Mybatis_plusTest {

    @Test
    void queryWrapperBuildsParameterizedSqlForNestedClausesGroupingAndPaging() {
        QueryWrapper<SampleEntity> queryWrapper = Wrappers.<SampleEntity>query()
                .select("user_id", "display_name", "status")
                .eq("status", "ACTIVE")
                .and(wrapper -> wrapper.ge("age", 18)
                        .likeRight("display_name", "Al"))
                .in("user_id", List.of(1L, 2L, 3L))
                .groupBy("status")
                .having("COUNT(*) > {0}", 1)
                .orderByDesc("created_at")
                .last("FETCH FIRST 5 ROWS ONLY");

        assertThat(queryWrapper.getSqlSelect()).isEqualTo("user_id,display_name,status");
        assertThat(queryWrapper.getSqlSegment())
                .contains("status = #{ew.paramNameValuePairs.")
                .contains("age >= #{ew.paramNameValuePairs.")
                .contains("display_name LIKE #{ew.paramNameValuePairs.")
                .contains("user_id IN (")
                .contains("GROUP BY status")
                .contains("HAVING COUNT(*) > #{ew.paramNameValuePairs.")
                .contains("ORDER BY created_at DESC")
                .contains("FETCH FIRST 5 ROWS ONLY");
        assertThat(queryWrapper.getCustomSqlSegment()).startsWith("WHERE ");
        assertThat(queryWrapper.getTargetSql())
                .contains("status = ?")
                .contains("age >= ?")
                .contains("display_name LIKE ?")
                .contains("user_id IN (?,?,?)")
                .contains("GROUP BY status")
                .contains("HAVING COUNT(*) > ?")
                .contains("ORDER BY created_at DESC");
        assertThat(queryWrapper.getParamAlias()).isEqualTo("ew");
        assertThat(queryWrapper.getParamNameValuePairs())
                .hasSize(7)
                .containsValue("ACTIVE")
                .containsValue(18)
                .containsValue("Al%")
                .containsValue(1L)
                .containsValue(2L)
                .containsValue(3L)
                .containsValue(1);
    }

    @Test
    void updateWrapperBuildsParameterizedAssignmentsPredicatesAndSubqueries() {
        UpdateWrapper<SampleEntity> updateWrapper = Wrappers.<SampleEntity>update()
                .set("display_name", "Updated")
                .set("status", "INACTIVE")
                .setSql("updated_at = CURRENT_TIMESTAMP")
                .eq("user_id", 1L)
                .isNotNull("created_at")
                .exists("select 1 from audit_log where audit_log.user_id = {0}", 1L);

        assertThat(updateWrapper.getSqlSet())
                .contains("display_name=#{ew.paramNameValuePairs.")
                .contains("status=#{ew.paramNameValuePairs.")
                .contains("updated_at = CURRENT_TIMESTAMP");
        assertThat(updateWrapper.getSqlSegment())
                .contains("user_id = #{ew.paramNameValuePairs.")
                .contains("created_at IS NOT NULL")
                .contains("EXISTS (select 1 from audit_log where audit_log.user_id = #{ew.paramNameValuePairs.");
        assertThat(updateWrapper.getTargetSql())
                .contains("user_id = ?")
                .contains("created_at IS NOT NULL")
                .contains("EXISTS (select 1 from audit_log where audit_log.user_id = ?)");

        Map<String, Object> parameters = updateWrapper.getParamNameValuePairs();
        assertThat(parameters)
                .hasSize(4)
                .containsValue("Updated")
                .containsValue("INACTIVE")
                .containsValue(1L);
    }

    @Test
    void wrapperClearResetsStateAndAllowsTheSameWrapperToBeReused() {
        QueryWrapper<SampleEntity> wrapper = Wrappers.<SampleEntity>query()
                .select("user_id")
                .eq("status", "ACTIVE")
                .orderByAsc("display_name");

        assertThat(wrapper.nonEmptyOfWhere()).isTrue();
        assertThat(wrapper.getTargetSql())
                .contains("status = ?")
                .contains("ORDER BY display_name ASC");

        wrapper.clear();

        assertThat(wrapper.getSqlSelect()).isNull();
        assertThat(wrapper.isEmptyOfWhere()).isTrue();
        assertThat(wrapper.getTargetSql()).isEmpty();
        assertThat(wrapper.getParamNameValuePairs()).isEmpty();

        wrapper.allEq(Map.of("status", "ACTIVE", "department", "PLATFORM"), false)
                .apply("score > {0}", 7)
                .orderByAsc("department");

        assertThat(wrapper.getTargetSql())
                .contains("status = ?")
                .contains("department = ?")
                .contains("score > ?")
                .contains("ORDER BY department ASC");
        assertThat(wrapper.getParamNameValuePairs())
                .hasSize(3)
                .containsValue("ACTIVE")
                .containsValue("PLATFORM")
                .containsValue(7);
    }

    @Test
    void mysqlPaginationDialectBuildsPaginationAndCountSql() {
        String originalSql = "SELECT user_id, display_name FROM sample_users WHERE status = ? ORDER BY created_at DESC";
        DialectModel dialectModel = DialectFactory.getDialect(DbType.MYSQL)
                .buildPaginationSql(originalSql, 3, 5);

        assertThat(dialectModel.getDialectSql())
                .isEqualTo("SELECT user_id, display_name FROM sample_users WHERE status = ? ORDER BY created_at DESC LIMIT ?,?");
        assertThat(SqlParserUtils.getOriginalCountSql(originalSql))
                .isEqualTo("SELECT COUNT(*) FROM (SELECT user_id, display_name FROM sample_users WHERE status = ? ORDER BY created_at DESC) TOTAL");
    }

    @Test
    void pageAndTableNameParserExposePagingOrderAndSqlTableMetadata() {
        OrderItem ascendingName = OrderItem.asc("display_name");
        OrderItem descendingCreatedAt = OrderItem.desc("created_at");
        SampleEntity firstRecord = new SampleEntity(1L, "Ada", "ACTIVE");
        SampleEntity secondRecord = new SampleEntity(2L, "Grace", "ACTIVE");

        Page<SampleEntity> page = Page.<SampleEntity>of(2, 3, 8)
                .addOrder(ascendingName, descendingCreatedAt)
                .setRecords(List.of(firstRecord, secondRecord));
        page.setCountId("countActiveUsers");
        page.setMaxLimit(10L);
        page.setOptimizeJoinOfCountSql(false);

        TableNameParser parser = new TableNameParser(
                "SELECT u.user_id, r.name FROM sample_users u "
                        + "JOIN user_roles r ON r.user_id = u.user_id "
                        + "WHERE EXISTS (SELECT 1 FROM user_audit a WHERE a.user_id = u.user_id)"
        );

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotal()).isEqualTo(8);
        assertThat(page.getPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.getRecords()).extracting(SampleEntity::getDisplayName).containsExactly("Ada", "Grace");
        assertThat(page.orders()).containsExactly(ascendingName, descendingCreatedAt);
        assertThat(page.countId()).isEqualTo("countActiveUsers");
        assertThat(page.maxLimit()).isEqualTo(10L);
        assertThat(page.optimizeJoinOfCountSql()).isFalse();

        assertThat(parser.tables()).containsExactlyInAnyOrder("sample_users", "user_roles", "user_audit");
    }

    static final class SampleEntity {
        private final Long id;
        private final String displayName;
        private final String status;

        SampleEntity(Long id, String displayName, String status) {
            this.id = id;
            this.displayName = displayName;
            this.status = status;
        }

        Long getId() {
            return id;
        }

        String getDisplayName() {
            return displayName;
        }

        String getStatus() {
            return status;
        }
    }
}
