package org_jooq.jooq.coverage;

import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.EnumType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.SQLDialect;
import org.jooq.SchemaMapping;
import org.jooq.Table;
import org.jooq.conf.MappedCatalog;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSqlExecutionCoverageTest {

    private enum Status implements EnumType {
        ACTIVE;

        @Override
        public String getLiteral() {
            return "active";
        }

        @Override
        public String getName() {
            return "status";
        }
    }

    @Test
    void executesAliasedJoinsSubqueriesAndLazyCursors() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jooq_deep;DB_CLOSE_DELAY=-1")) {
            DSLContext dsl = DSL.using(connection, SQLDialect.H2);
            dsl.execute("create table authors (id int primary key, name varchar(30))");
            dsl.execute("create table books (id int primary key, author_id int, title varchar(30), price int)");
            dsl.batch(
                    dsl.insertInto(DSL.table("authors")).columns(DSL.field("id"), DSL.field("name"))
                            .values(1, "Ada"),
                    dsl.insertInto(DSL.table("authors")).columns(DSL.field("id"), DSL.field("name"))
                            .values(2, "Bob"),
                    dsl.insertInto(DSL.table("books")).columns(DSL.field("id"), DSL.field("author_id"),
                            DSL.field("title"), DSL.field("price")).values(10, 1, "Compiler", 20),
                    dsl.insertInto(DSL.table("books")).columns(DSL.field("id"), DSL.field("author_id"),
                            DSL.field("title"), DSL.field("price")).values(11, 1, "Types", 30)
            ).execute();

            Table<Record> authors = DSL.table(DSL.name("AUTHORS")).as("a");
            Table<Record> books = DSL.table(DSL.name("BOOKS")).as("b");
            Field<Integer> authorId = DSL.field(DSL.name("a", "ID"), Integer.class);
            Field<Integer> bookAuthorId = DSL.field(DSL.name("b", "AUTHOR_ID"), Integer.class);
            Field<String> authorName = DSL.field(DSL.name("a", "NAME"), String.class).as("author_name");
            Field<String> title = DSL.field(DSL.name("b", "TITLE"), String.class).as("book_title");
            Field<Integer> price = DSL.field(DSL.name("b", "PRICE"), Integer.class);

            try (Cursor<Record3<String, String, Integer>> cursor = dsl.select(authorName, title, price)
                    .from(authors.leftJoin(books).on(authorId.eq(bookAuthorId)))
                    .where(price.in(dsl.select(DSL.field("PRICE", Integer.class)).from("BOOKS")))
                    .orderBy(price.desc())
                    .fetchLazy()) {
                List<Record3<String, String, Integer>> rows = cursor.fetchNext(10);
                assertThat(rows).hasSize(2);
                assertThat(rows.get(0).get(authorName)).isEqualTo("Ada");
                assertThat(rows).extracting(row -> row.get(title)).containsExactly("Types", "Compiler");
            }
        }
    }

    @Test
    void rendersCtesWindowsRowsAndDmlThroughPublicDsl() {
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        Field<Integer> id = DSL.field(DSL.name("id"), Integer.class);
        Table<Record> input = DSL.table(DSL.name("input")).as("i");
        String selectSql = postgres.renderInlined(
                DSL.with("recent").as(DSL.select(id).from(input).where(id.in(1, 2, 3)))
                        .select(id, DSL.count().over().as("total"))
                        .from(DSL.table("recent"))
                        .where(DSL.row(id, DSL.inline("active")).in(DSL.row(1, "active"))));
        String insertSql = postgres.renderInlined(
                DSL.insertInto(DSL.table("input"), id, DSL.field("state"))
                        .values(1, "active").values(2, "active"));
        String deleteSql = postgres.renderInlined(
                DSL.deleteFrom(DSL.table("input")).where(id.in(DSL.select(id).from(input))));

        assertThat(selectSql).contains("with", "count", "over");
        assertThat(insertSql).contains("insert into", "values");
        assertThat(deleteSql).contains("delete from", "select");
    }

    @Test
    void rendersScalarFunctionsAndTypedCastsThroughPublicDsl() {
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        Field<Integer> number = DSL.val(2);
        String sql = postgres.renderInlined(DSL.select(
                DSL.acos(number), DSL.acosh(number), DSL.acoth(number), DSL.asin(number), DSL.asinh(number),
                DSL.atan(number), DSL.atan2(number, 1), DSL.atanh(number), DSL.ascii("jooq"), DSL.avg(number),
                DSL.cast(number, SQLDataType.VARCHAR),
                DSL.cast(DSL.val(new Integer[] {1, 2}), SQLDataType.INTEGER.getArrayDataType())));

        assertThat(sql).contains("acos", "ascii", "cast");
    }

    @Test
    void rendersArrayExpressionsAndDialectSpecificDdlThroughPublicDsl() {
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        Field<Integer[]> values = DSL.val(new Integer[] {1, 2, 3});
        Field<Integer[]> replacement = DSL.val(new Integer[] {2, 3, 4});
        String arraySql = postgres.renderInlined(DSL.select(
                DSL.arrayAppend(values, 4), DSL.arrayPrepend(0, values), DSL.arrayConcat(values, replacement),
                DSL.arrayRemove(values, 2), DSL.arrayReplace(values, DSL.val(2), DSL.val(9)),
                DSL.arrayGet(values, 1))
                .where(DSL.arrayOverlap(values, replacement)));
        String ddlSql = postgres.renderInlined(DSL.alterTable("items").add("tags",
                SQLDataType.INTEGER.getArrayDataType()));
        String schemaSql = postgres.renderInlined(DSL.alterSchema("old_schema").renameTo("new_schema"));
        String sequenceSql = postgres.renderInlined(DSL.alterSequence("old_sequence").renameTo("new_sequence"));
        String typeSql = postgres.renderInlined(DSL.alterType("status").addValue("active"));
        String viewSql = postgres.renderInlined(DSL.alterView("items_view").as(DSL.selectOne()));
        String indexSql = postgres.renderInlined(DSL.alterIndex("items_idx").on("items").renameTo("new_items_idx"));

        assertThat(arraySql).contains("array", "select");
        assertThat(ddlSql).contains("alter table", "tags");
        assertThat(schemaSql).contains("alter schema");
        assertThat(sequenceSql).contains("alter sequence");
        assertThat(typeSql).contains("alter type");
        assertThat(viewSql).contains("drop view", "create view");
        assertThat(indexSql).contains("alter index");
    }

    @Test
    void rendersConstraintsAndStringAndBitExpressionsThroughPublicDsl() {
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        Field<Integer> number = DSL.val(6);
        Field<String> text = DSL.val("  jooq  ");
        Field<Status> status = DSL.val(Status.ACTIVE, SQLDataType.VARCHAR.asEnumDataType(Status.class));
        String ddl = postgres.renderInlined(DSL.createTable("child")
                .column("parent_id", SQLDataType.INTEGER)
                .constraints(
                        DSL.constraint("child_parent").foreignKey("parent_id").references("parent", "id"),
                        DSL.constraint("positive_parent").check(DSL.field("parent_id", Integer.class).ge(0))));
        String expressions = postgres.renderInlined(DSL.select(
                DSL.concat(text, "!"), DSL.binaryLength(new byte[] {1, 2}), DSL.ltrim(text), DSL.rtrim(text),
                DSL.trim(text), DSL.md5(text), DSL.octetLength(text), DSL.overlay(text, "x", 2, 1),
                DSL.position(text, "q"), DSL.substring(text, 2, 3), DSL.charLength(text), DSL.chr(number),
                DSL.cbrt(number), DSL.ceil(number), DSL.bitAnd(number, 3), DSL.bitCount(number),
                DSL.bitGet(number, 1), DSL.bitNot(number), DSL.bitOr(number, 3), DSL.bitSet(number, 1),
                DSL.bitXor(number, 3), DSL.bitNand(number, 3), DSL.bitNor(number, 3),
                DSL.bitXNor(number, 3), DSL.cardinality(DSL.val(new Integer[] {1, 2})),
                DSL.case_().when(number.gt(0), "positive").otherwise("negative"), DSL.choose(1, "a", "b"),
                DSL.bitAndAgg(number), DSL.bitNandAgg(number), DSL.bitNorAgg(number), DSL.bitOrAgg(number),
                DSL.bitXNorAgg(number), DSL.bitXorAgg(number), DSL.boolAnd(number.gt(0)),
                DSL.boolOr(number.gt(0)), DSL.listAgg(number).withinGroupOrderBy(number), status));
        String arrayTable = postgres.renderInlined(DSL.selectFrom(DSL.unnest(new Integer[] {1, 2}).as("n")));
        String derbyCast = DSL.using(SQLDialect.DERBY).renderInlined(DSL.select(DSL.cast(text, SQLDataType.VARCHAR)));

        assertThat(ddl).contains("foreign key", "check");
        assertThat(expressions).contains("||", "bit", "case", "substring");
        assertThat(arrayTable).contains("unnest");
        assertThat(derbyCast).contains("cast");
    }

    @Test
    void rendersDialectBranchesForDdlAggregatesAndScalarExpressions() {
        Field<Integer> number = DSL.field(DSL.name("amount"), Integer.class);
        Field<String> text = DSL.field(DSL.name("label"), String.class);
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        DSLContext mysql = DSL.using(SQLDialect.MYSQL);
        DSLContext clickHouse = DSL.using(SQLDialect.CLICKHOUSE);

        String aggregates = postgres.renderInlined(DSL.select(
                DSL.anyValue(number), DSL.covarPop(number, number), DSL.covarSamp(number, number),
                DSL.binaryListAgg(text).withinGroupOrderBy(text), DSL.cumeDist().over(),
                DSL.count().filterWhere(number.gt(0)), DSL.coalesce(text, "missing"), DSL.cos(number),
                DSL.cosh(number), DSL.cot(number), DSL.coth(number), DSL.corr(number, number),
                DSL.currentCatalog(), DSL.currentSchema(), DSL.currentUser(), DSL.currentDate(),
                DSL.currentTime(), DSL.currentTimestamp()).from("sales").groupBy(DSL.cube(number)));
        String windows = postgres.renderInlined(DSL.select(DSL.count().over(DSL.name("w"))).from("sales")
                .window(DSL.name("w").as(DSL.partitionBy(number))));
        String postgresDdl = postgres.renderInlined(DSL.alterTableIfExists("sales").addColumnIfNotExists("note",
                SQLDataType.VARCHAR(40)));
        String mysqlDdl = mysql.renderInlined(DSL.alterTableIfExists("sales").dropColumnIfExists("note").cascade());
        String renamed = mysql.renderInlined(DSL.alterTable("sales").renameColumn("amount").to("total"));
        String altered = postgres.renderInlined(DSL.alterTable("sales").alterColumn("amount")
                .set(SQLDataType.DECIMAL(12, 2).nullable(false)));
        String views = mysql.renderInlined(DSL.alterViewIfExists("sales_view").as(DSL.selectFrom("sales")));
        String create = postgres.renderInlined(DSL.createTableIfNotExists("sales_copy").as(DSL.selectFrom("sales")));
        String objects = postgres.renderInlined(DSL.createDatabaseIfNotExists("archive"))
                + postgres.renderInlined(DSL.createDomainIfNotExists("money").as(SQLDataType.DECIMAL(12, 2)))
                + postgres.renderInlined(DSL.createIndexIfNotExists("sales_amount").on("sales", "amount"))
                + postgres.renderInlined(DSL.createSequenceIfNotExists("sales_sequence"))
                + postgres.renderInlined(DSL.createTypeIfNotExists("sales_status").asEnum("new", "closed"));
        String arrays = postgres.renderInlined(DSL.selectFrom(DSL.unnest(new Integer[] {1, 2}).as("values")))
                + clickHouse.renderInlined(DSL.selectFrom(DSL.unnest(new Integer[] {1, 2}).as("values")));

        assertThat(aggregates).contains("any_value", "covar", "coalesce", "current");
        assertThat(windows).contains("window", "partition by");
        assertThat(postgresDdl).contains("alter table", "if not exists");
        assertThat(mysqlDdl).contains("drop");
        assertThat(renamed).contains("rename");
        assertThat(altered).contains("decimal");
        assertThat(views).contains("view");
        assertThat(create).contains("create table", "as select");
        assertThat(objects).contains("create", "archive", "money");
        assertThat(arrays).contains("unnest");
    }

    @Test
    void executesConvertedValuesAndAttachesPublicQueryParts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jooq_binding;DB_CLOSE_DELAY=-1")) {
            DSLContext dsl = DSL.using(connection, SQLDialect.H2);
            Field<String> converted = DSL.val(7, SQLDataType.INTEGER.asConvertedDataType(
                    String.class, Object::toString, Integer::valueOf));
            org.jooq.Select<?> query = dsl.select(converted.as("value")).where(converted.eq("7"));
            query.attach(dsl.configuration());

            assertThat(query.fetchOne(0, String.class)).isEqualTo("7");
        }
    }

    @Test
    void rendersDomainsDefaultsAndDialectSpecificPublicExpressions() {
        DSLContext postgres = DSL.using(SQLDialect.POSTGRES);
        org.jooq.Domain<Integer> quantity = DSL.domain(DSL.name("inventory", "quantity"), SQLDataType.INTEGER);
        org.jooq.Domain<?> unresolvedDomain = DSL.domain(DSL.name("inventory", "unresolved_quantity"));
        Field<Integer> amount = DSL.field(DSL.name("amount"), Integer.class);
        String expressions = postgres.renderInlined(DSL.select(
                DSL.dateAdd(Date.valueOf("2024-01-01"), 2, org.jooq.DatePart.DAY),
                DSL.dateDiff(org.jooq.DatePart.DAY, Date.valueOf("2024-01-01"), Date.valueOf("2024-01-03")),
                DSL.digits(amount), DSL.denseRank().over().as("rank")));
        String createDomainTable = postgres.renderInlined(DSL.createTable("inventory_item")
                .column("quantity", quantity.getDataType().default_(DSL.inline(1)))
                .column("unresolved_quantity", unresolvedDomain.getDataType())
                .column("optional_quantity", SQLDataType.INTEGER.default_(DSL.inline(2))));
        String index = postgres.renderInlined(DSL.alterIndexIfExists("inventory_idx").on("inventory_item")
                .renameTo("inventory_item_idx"));
        String view = postgres.renderInlined(DSL.createViewIfNotExists("inventory_view", "quantity")
                .as(DSL.select(DSL.inline(1).as("quantity"))));

        assertThat(expressions).contains("date", "lpad", "dense_rank");
        assertThat(createDomainTable).contains("inventory_item", "quantity", "default");
        assertThat(index).contains("alter index", "if exists");
        assertThat(view).contains("create view", "exception");
    }

    @Test
    void mapsPublicRecordShapesAndExecutesTheFailurePath() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:jooq_mapping;DB_CLOSE_DELAY=-1")) {
            DSLContext dsl = DSL.using(connection, SQLDialect.H2);
            dsl.execute("create table mapped_scores (id int primary key, score int)");
            dsl.execute("insert into mapped_scores values (1, 10), (2, 20)");

            List<MutableScore> mutable = dsl.select(DSL.field("id"), DSL.field("score"))
                    .from("mapped_scores").orderBy(DSL.field("id")).fetchInto(MutableScore.class);
            List<ImmutableScore> immutable = dsl.select(DSL.field("id"), DSL.field("score"))
                    .from("mapped_scores").orderBy(DSL.field("id")).fetchInto(ImmutableScore.class);
            Integer scalar = dsl.select(DSL.field("score", Integer.class)).from("mapped_scores")
                    .where(DSL.field("id").eq(1)).fetchOneInto(Integer.class);

            assertThat(mutable).extracting(score -> score.score).containsExactly(10, 20);
            assertThat(immutable).extracting(ImmutableScore::score).containsExactly(10, 20);
            assertThat(scalar).isEqualTo(10);
            org.junit.jupiter.api.Assertions.assertThrows(org.jooq.exception.DataAccessException.class,
                    () -> dsl.fetch("select missing_column from mapped_scores"));
        }
    }

    public static final class MutableScore {
        public Integer id;
        public Integer score;
    }

    public record ImmutableScore(Integer id, Integer score) {
    }

    @Test
    void mapsCatalogAndSchemaNamesWhileRenderingPublicTables() {
        MappedSchema schema = new MappedSchema().withInput("legacy").withOutput("application");
        MappedCatalog catalog = new MappedCatalog().withInput("old_catalog").withOutput("new_catalog")
                .withSchemata(schema);
        Settings settings = new Settings().withRenderMapping(new RenderMapping().withCatalogs(catalog));
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES, settings);
        SchemaMapping mapping = dsl.configuration().schemaMapping();
        String sql = dsl.render(DSL.selectFrom(DSL.table(DSL.name("old_catalog", "legacy", "items")).as("item")));

        assertThat(mapping).isNotNull();
        assertThat(sql).contains("new_catalog", "application", "items", "item");
    }
}
