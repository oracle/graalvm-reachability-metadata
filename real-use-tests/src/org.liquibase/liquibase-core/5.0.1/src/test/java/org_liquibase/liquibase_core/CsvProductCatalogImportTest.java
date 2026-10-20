/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises CSV reference-data loading to provision an application's product catalog.
@Timeout(60)
public class CsvProductCatalogImportTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void importsQuotedProductNamesAndTypedPricesAndAvailability() throws Exception {
        Path dataDirectory = Files.createDirectory(changelogDirectory.resolve("data"));
        Files.writeString(dataDirectory.resolve("products.csv"), """
                sku,name,price,available
                COFFEE-250,"Coffee, ground",12.50,true
                TEA-OOLONG,"Oolong ""Reserve""\",8.25,false
                """);
        Files.writeString(changelogDirectory.resolve("catalog.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="import-product-catalog" author="catalog-service">
                        <loadData tableName="product_catalog" file="data/products.csv"
                            relativeToChangelogFile="true" encoding="UTF-8" usePreparedStatements="true">
                            <column header="sku" name="sku" type="STRING"/>
                            <column header="name" name="display_name" type="STRING"/>
                            <column header="price" name="unit_price" type="NUMERIC"/>
                            <column header="available" name="available" type="BOOLEAN"/>
                        </loadData>
                    </changeSet>
                </databaseChangeLog>
                """);

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:csv_product_catalog", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE product_catalog (
                            sku VARCHAR(40) PRIMARY KEY,
                            display_name VARCHAR(100) NOT NULL,
                            unit_price DECIMAL(10, 2) NOT NULL,
                            available BOOLEAN NOT NULL
                        )
                        """);
            }
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("catalog.xml", resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("""
                             SELECT sku, display_name, unit_price, available
                             FROM product_catalog ORDER BY sku
                             """)) {
                    assertTrue(rows.next());
                    assertEquals("COFFEE-250", rows.getString("sku"));
                    assertEquals("Coffee, ground", rows.getString("display_name"));
                    assertEquals(new BigDecimal("12.50"), rows.getBigDecimal("unit_price"));
                    assertTrue(rows.getBoolean("available"));

                    assertTrue(rows.next());
                    assertEquals("TEA-OOLONG", rows.getString("sku"));
                    assertEquals("Oolong \"Reserve\"", rows.getString("display_name"));
                    assertEquals(new BigDecimal("8.25"), rows.getBigDecimal("unit_price"));
                    assertFalse(rows.getBoolean("available"));
                    assertFalse(rows.next(), "Each CSV product must be imported exactly once");
                }
            }
        }
    }
}
