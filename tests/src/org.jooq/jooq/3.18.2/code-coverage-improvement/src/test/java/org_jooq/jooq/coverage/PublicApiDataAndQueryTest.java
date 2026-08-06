package org_jooq.jooq.coverage;

import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.conf.AutoAliasExpressions;
import org.jooq.conf.FetchIntermediateResult;
import org.jooq.conf.InvocationOrder;
import org.jooq.conf.MigrationDefaultContentType;
import org.jooq.conf.MigrationSchema;
import org.jooq.conf.ParseNameCase;
import org.jooq.conf.ParseUnsupportedSyntax;
import org.jooq.conf.ParseWithMetaLookups;
import org.jooq.conf.Settings;
import org.jooq.conf.Transformation;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiDataAndQueryTest {

    @Test
    void convertsDataTypesThroughDirectionalPublicConverters() {
        DataType<Integer> integers = SQLDataType.INTEGER;
        DataType<String> strings = integers.asConvertedDataType(String.class, Object::toString, Integer::valueOf);
        DataType<String> fromOnly = integers.asConvertedDataTypeFrom(String.class, Object::toString);
        DataType<String> inferredFrom = integers.asConvertedDataTypeFrom(Object::toString);
        DataType<String> toOnly = integers.asConvertedDataTypeTo(String.class, Integer::valueOf);
        DataType<String> inferredTo = integers.asConvertedDataTypeTo(Integer::valueOf);

        assertThat(strings.convert("12")).isEqualTo("12");
        assertThat(strings.getConverter().to("12")).isEqualTo(12);
        assertThat(fromOnly.convert(7)).isEqualTo("7");
        assertThat(inferredFrom.convert(8)).isEqualTo("8");
        assertThat(toOnly.getConverter().to("9")).isEqualTo(9);
        assertThat(inferredTo.getConverter().to("10")).isEqualTo(10);
    }

    @Test
    void collectsAndStreamsDatabaseResultsUsingPublicCollectors() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jooq_coverage;DB_CLOSE_DELAY=-1")) {
            DSLContext dsl = DSL.using(connection, SQLDialect.H2);
            dsl.execute("create table scores (id int primary key, name varchar(20), score int)");
            dsl.execute("insert into scores values (1, 'Ada', 10), (2, 'Bob', 20), (3, 'Ava', 30)");

            Result<Record> rows = dsl.fetch("select id, name, score from scores order by id");
            assertThat(rows.stream().collect(org.jooq.Records.intoList(record -> record.get("NAME", String.class))))
                    .containsExactly("Ada", "Bob", "Ava");
            assertThat(rows.stream().collect(org.jooq.Records.intoSet(record -> record.get("NAME", String.class))))
                    .containsExactlyInAnyOrder("Ada", "Bob", "Ava");
            Map<Integer, String> names = rows.stream().collect(org.jooq.Records.intoMap(
                    record -> record.get("ID", Integer.class), record -> record.get("NAME", String.class)));
            assertThat(names).containsEntry(2, "Bob");
            Map<String, List<Integer>> scores = rows.stream().collect(org.jooq.Records.intoGroups(
                    record -> record.get("NAME", String.class).substring(0, 1),
                    record -> record.get("SCORE", Integer.class)));
            assertThat(scores).containsEntry("A", List.of(10, 30));
            assertThat(rows.stream().collect(org.jooq.Records.intoArray(String.class,
                    record -> record.get("NAME", String.class)))).containsExactly("Ada", "Bob", "Ava");

            ArrayList<Integer> ids = new ArrayList<>();
            dsl.select(DSL.field("id", Integer.class)).from("scores").orderBy(DSL.field("id")).forEach(
                    record -> ids.add(record.value1()));
            assertThat(ids).containsExactly(1, 2, 3);
            assertThat(dsl.select(DSL.field("id", Integer.class)).from("scores").spliterator().estimateSize())
                    .isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void preservesRenderingParsingAndMigrationSettings() {
        MigrationSchema schema = new MigrationSchema().withCatalog("app").withSchema("history");
        Settings settings = new Settings()
                .withRenderLocale(Locale.CANADA_FRENCH)
                .withRenderFormatted(true)
                .withRenderAutoAliasedDerivedTableExpressions(AutoAliasExpressions.NEVER)
                .withFetchIntermediateResult(FetchIntermediateResult.WHEN_RESULT_REQUESTED)
                .withBatchSize(50)
                .withParseDialect(SQLDialect.H2)
                .withParseLocale(Locale.JAPAN)
                .withParseNameCase(ParseNameCase.LOWER)
                .withParseWithMetaLookups(ParseWithMetaLookups.IGNORE_ON_FAILURE)
                .withParseUnsupportedSyntax(ParseUnsupportedSyntax.IGNORE)
                .withTransformGroupByColumnIndex(Transformation.ALWAYS)
                .withTransformInlineCTE(Transformation.NEVER)
                .withTransformQualify(Transformation.ALWAYS)
                .withTransformRownum(Transformation.NEVER)
                .withMigrationHistorySchema(schema)
                .withMigrationDefaultSchema(schema)
                .withMigrationDefaultContentType(MigrationDefaultContentType.SCRIPT)
                .withMigrationListenerStartInvocationOrder(InvocationOrder.REVERSE)
                .withMigrationListenerEndInvocationOrder(InvocationOrder.DEFAULT);

        assertThat(settings.getRenderLocale()).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(settings.isRenderFormatted()).isTrue();
        assertThat(settings.getRenderAutoAliasedDerivedTableExpressions()).isEqualTo(AutoAliasExpressions.NEVER);
        assertThat(settings.getFetchIntermediateResult()).isEqualTo(FetchIntermediateResult.WHEN_RESULT_REQUESTED);
        assertThat(settings.getBatchSize()).isEqualTo(50);
        assertThat(settings.getParseDialect()).isEqualTo(SQLDialect.H2);
        assertThat(settings.getParseLocale()).isEqualTo(Locale.JAPAN);
        assertThat(settings.getParseNameCase()).isEqualTo(ParseNameCase.LOWER);
        assertThat(settings.getParseWithMetaLookups()).isEqualTo(ParseWithMetaLookups.IGNORE_ON_FAILURE);
        assertThat(settings.getParseUnsupportedSyntax()).isEqualTo(ParseUnsupportedSyntax.IGNORE);
        assertThat(settings.getTransformGroupByColumnIndex()).isEqualTo(Transformation.ALWAYS);
        assertThat(settings.getTransformInlineCTE()).isEqualTo(Transformation.NEVER);
        assertThat(settings.getTransformQualify()).isEqualTo(Transformation.ALWAYS);
        assertThat(settings.getTransformRownum()).isEqualTo(Transformation.NEVER);
        assertThat(settings.getMigrationHistorySchema()).isEqualTo(schema);
        assertThat(settings.getMigrationDefaultSchema()).isEqualTo(schema);
        assertThat(settings.getMigrationDefaultContentType()).isEqualTo(MigrationDefaultContentType.SCRIPT);
        assertThat(settings.getMigrationListenerStartInvocationOrder()).isEqualTo(InvocationOrder.REVERSE);
        assertThat(settings.getMigrationListenerEndInvocationOrder()).isEqualTo(InvocationOrder.DEFAULT);
    }
}
