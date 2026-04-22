/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus;

import com.baomidou.mybatisplus.core.MybatisPlusVersion;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlLike;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.TableNameParser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlScriptUtils;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class Mybatis_plusTest {
    @Test
    void queryWrapperBuildsComplexPredicatesAndCanBeCleared() {
        QueryWrapper<Object> queryWrapper = Wrappers.<Object>query()
                .select("id", "name")
                .eq("status", "ACTIVE")
                .nested(wrapper -> wrapper.ge("age", 18).lt("age", 65))
                .groupBy("role")
                .having("count(*) > {0}", 1)
                .orderByDesc("created_at")
                .last("limit 5");

        assertThat(queryWrapper.getSqlSelect()).isEqualTo("id,name");
        assertThat(normalizeSql(queryWrapper.getCustomSqlSegment()))
                .isEqualTo("WHERE (status = #{ew.paramNameValuePairs.MPGENVAL1} AND (age >= #{ew.paramNameValuePairs.MPGENVAL2} AND age < #{ew.paramNameValuePairs.MPGENVAL3})) GROUP BY role HAVING count(*) > #{ew.paramNameValuePairs.MPGENVAL4} ORDER BY created_at DESC limit 5");
        assertThat(normalizeSql(queryWrapper.getTargetSql()))
                .isEqualTo("(status = ? AND (age >= ? AND age < ?)) GROUP BY role HAVING count(*) > ? ORDER BY created_at DESC limit 5");
        assertThat(queryWrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("ACTIVE", 18, 65, 1);

        queryWrapper.clear();

        assertThat(queryWrapper.getSqlSelect()).isNull();
        assertThat(queryWrapper.getTargetSql()).isEmpty();
        assertThat(queryWrapper.getParamNameValuePairs()).isEmpty();
        assertThat(Wrappers.emptyWrapper().isEmptyOfWhere()).isTrue();
    }

    @Test
    void updateWrapperTracksAssignmentsAndPredicateParameters() {
        UpdateWrapper<Object> updateWrapper = Wrappers.<Object>update()
                .set("name", "Neo")
                .setSql("updated_at = CURRENT_TIMESTAMP")
                .eq("id", 7)
                .in("status", List.of("ACTIVE", "LOCKED"));

        assertThat(updateWrapper.getSqlSet())
                .isEqualTo("name=#{ew.paramNameValuePairs.MPGENVAL1},updated_at = CURRENT_TIMESTAMP");
        assertThat(normalizeSql(updateWrapper.getCustomSqlSegment()))
                .isEqualTo("WHERE (id = #{ew.paramNameValuePairs.MPGENVAL2} AND status IN (#{ew.paramNameValuePairs.MPGENVAL3},#{ew.paramNameValuePairs.MPGENVAL4}))");
        assertThat(normalizeSql(updateWrapper.getTargetSql()))
                .isEqualTo("(id = ? AND status IN (?,?))");
        assertThat(updateWrapper.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder("Neo", 7, "ACTIVE", "LOCKED");
    }

    @Test
    void pageConvertMutatesRecordsAndPreservesPagingConfiguration() {
        Page<String> page = Page.<String>of(3, 2, 5, false)
                .setRecords(List.of("neo", "trinity"))
                .setSearchCount(false)
                .setOptimizeCountSql(false)
                .addOrder(OrderItem.desc("created_at"));
        page.setOptimizeJoinOfCountSql(false);
        page.setMaxLimit(50L);
        page.setCountId("customCount");

        IPage<Integer> convertedPage = page.convert(String::length);

        assertThat((Object) convertedPage).isSameAs(page);
        assertThat(convertedPage.getRecords()).containsExactly(3, 7);
        assertThat(convertedPage.searchCount()).isFalse();
        assertThat(convertedPage.optimizeCountSql()).isFalse();
        assertThat(convertedPage.optimizeJoinOfCountSql()).isFalse();
        assertThat(convertedPage.maxLimit()).isEqualTo(50L);
        assertThat(convertedPage.countId()).isEqualTo("customCount");
        assertThat(convertedPage.offset()).isEqualTo(4);
        assertThat(convertedPage.orders())
                .extracting(OrderItem::getColumn, OrderItem::isAsc)
                .containsExactly(tuple("created_at", false));
    }

    @Test
    void pageConvertSkipsMapperInvocationWhenNoRecordsArePresent() {
        Page<String> page = Page.<String>of(1, 5)
                .setRecords(List.of());
        AtomicInteger mappingCount = new AtomicInteger();

        IPage<Integer> convertedPage = page.convert(value -> {
            mappingCount.incrementAndGet();
            return value.length();
        });

        assertThat((Object) convertedPage).isSameAs(page);
        assertThat(convertedPage.getRecords()).isEmpty();
        assertThat(mappingCount).hasValue(0);
    }

    @Test
    void pageAndOrderFactoriesRetainPaginationState() {
        Page<String> page = Page.<String>of(2, 3, 8)
                .setRecords(List.of("alpha", "beta"))
                .addOrder(OrderItem.asc("name"), OrderItem.desc("created_at"));

        assertThat(page.getCurrent()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotal()).isEqualTo(8);
        assertThat(page.getPages()).isEqualTo(3);
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.hasNext()).isTrue();
        assertThat(page.getRecords()).containsExactly("alpha", "beta");
        assertThat(page.orders())
                .extracting(OrderItem::getColumn, OrderItem::isAsc)
                .containsExactly(
                        tuple("name", true),
                        tuple("created_at", false)
                );
        assertThat(OrderItem.ascs("first_name", "last_name"))
                .extracting(OrderItem::getColumn, OrderItem::isAsc)
                .containsExactly(
                        tuple("first_name", true),
                        tuple("last_name", true)
                );
        assertThat(OrderItem.descs("updated_at"))
                .extracting(OrderItem::getColumn, OrderItem::isAsc)
                .containsExactly(tuple("updated_at", false));
    }

    @Test
    void sqlUtilitiesAndStringHelpersCoverCommonPublicHelpers() {
        assertThat(SqlScriptUtils.safeParam("name")).isEqualTo("#{name}");
        assertThat(SqlScriptUtils.unSafeParam("name")).isEqualTo("${name}");
        assertThat(normalizeSql(SqlScriptUtils.convertIf("name=#{name}", "name != null", true)))
                .isEqualTo("<if test=\"name != null\"> name=#{name} </if>");
        assertThat(normalizeSql(SqlScriptUtils.convertWhere(" AND name=#{name}")))
                .isEqualTo("<where> AND name=#{name} </where>");
        assertThat(normalizeSql(SqlScriptUtils.convertTrim("name=#{name},", "SET", null, null, ",")))
                .isEqualTo("<trim prefix=\"SET\" suffixOverrides=\",\"> name=#{name}, </trim>");
        assertThat(normalizeSql(SqlScriptUtils.convertForeach("#{item}", "items", null, "item", ",")))
                .isEqualTo("<foreach collection=\"items\" item=\"item\" separator=\",\"> #{item} </foreach>");
        assertThat(SqlUtils.concatLike("neo", SqlLike.DEFAULT)).isEqualTo("%neo%");
        assertThat(new TableNameParser("SELECT u.id, o.id FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 1").tables())
                .containsExactlyInAnyOrder("users", "orders");
        assertThat(MybatisPlusVersion.getVersion()).matches("\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9]+)?");
        assertThat(StringUtils.camelToUnderline("createdAt")).isEqualTo("created_at");
        assertThat(StringUtils.underlineToCamel("created_at")).isEqualTo("createdAt");
        assertThat(StringUtils.sqlArgsFill("name={0}, state={1}", "Neo", "ACTIVE"))
                .isEqualTo("name='Neo', state='ACTIVE'");
        assertThat(StringUtils.quotaMarkList(List.of("neo", 7))).isEqualTo("('neo',7)");
    }

    private static String normalizeSql(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
