/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jooq.jooq;

import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises jOOQ's recursive common-table-expression subsystem to build organization subtree breadcrumbs.
 */
public class OrganizationHierarchyTest {
    private static final Table<Record> UNITS = DSL.table(DSL.name("organization_unit"));
    private static final Field<Integer> ID = DSL.field(DSL.name("unit_id"), Integer.class);
    private static final Field<Integer> PARENT_ID = DSL.field(DSL.name("parent_id"), Integer.class);
    private static final Field<String> LABEL = DSL.field(DSL.name("label"), String.class);
    private static final Table<Record> TREE = DSL.table(DSL.name("subtree"));
    private static final Field<Integer> DESCENDANT_ID = DSL.field(
            DSL.name("subtree", "descendant_id"), Integer.class);
    private static final Field<String> DESCENDANT_LABEL = DSL.field(
            DSL.name("subtree", "descendant_label"), String.class);
    private static final Field<Integer> DEPTH = DSL.field(DSL.name("subtree", "depth"), Integer.class);
    private static final Field<String> BREADCRUMB = DSL.field(DSL.name("subtree", "breadcrumb"), String.class);

    @Test
    @Timeout(60)
    public void buildsBreadcrumbsRelativeToTheSelectedDepartment() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:organization_hierarchy", "sa", "")) {
            DSLContext database = DSL.using(connection, SQLDialect.H2);
            database.createTable(UNITS)
                    .column(ID, SQLDataType.INTEGER.nullable(false))
                    .column(PARENT_ID, SQLDataType.INTEGER)
                    .column(LABEL, SQLDataType.VARCHAR(80).nullable(false))
                    .primaryKey(ID)
                    .constraint(DSL.foreignKey(PARENT_ID).references(UNITS, ID))
                    .execute();
            database.insertInto(UNITS, ID, PARENT_ID, LABEL)
                    .values(1, (Integer) null, "Company")
                    .values(2, 1, "Engineering")
                    .values(3, 2, "Platform")
                    .values(4, 3, "API")
                    .values(5, 2, "Quality")
                    .values(6, 1, "Operations")
                    .values(7, 6, "Support")
                    .execute();

            // Ancestors and sibling departments must not leak into the chosen subtree.
            assertEquals(List.of(
                    new Department(2, "Engineering", 0, "Engineering"),
                    new Department(3, "Platform", 1, "Engineering / Platform"),
                    new Department(4, "API", 2, "Engineering / Platform / API"),
                    new Department(5, "Quality", 1, "Engineering / Quality")), subtree(database, 2));

            // A leaf is its own subtree, and an unknown department has no descendants.
            assertEquals(List.of(new Department(4, "API", 0, "API")), subtree(database, 4));
            assertEquals(List.of(), subtree(database, 99));
        }
    }

    private static List<Department> subtree(DSLContext database, int rootId) {
        CommonTableExpression<Record4<Integer, String, Integer, String>> subtree = DSL.name("subtree")
                .fields("descendant_id", "descendant_label", "depth", "breadcrumb")
                .as(DSL.select(ID, LABEL, DSL.inline(0), LABEL.cast(SQLDataType.VARCHAR(500)))
                        .from(UNITS)
                        .where(ID.eq(rootId))
                        .unionAll(DSL.select(ID, LABEL, DEPTH.add(1), BREADCRUMB.concat(" / ").concat(LABEL))
                                .from(UNITS)
                                .join(TREE).on(PARENT_ID.eq(DESCENDANT_ID))));

        return database.withRecursive(subtree)
                .select(DESCENDANT_ID, DESCENDANT_LABEL, DEPTH, BREADCRUMB)
                .from(subtree)
                .orderBy(BREADCRUMB, DESCENDANT_ID)
                .fetch(Records.mapping(Department::new));
    }

    private record Department(int id, String label, int depth, String breadcrumb) {
    }
}
