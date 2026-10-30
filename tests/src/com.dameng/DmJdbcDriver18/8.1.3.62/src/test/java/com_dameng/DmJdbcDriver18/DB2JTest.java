/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_dameng.DmJdbcDriver18;

import dm.jdbc.driver.DmdbSQLInput;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialStruct;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DB2JTest {
    @Test
    void readsStructuredSqlValueUsingApplicationTypeMap() throws Exception {
        Map<String, Class<?>> typeMap = Map.of("INVENTORY_ITEM", InventoryItem.class);
        SerialStruct struct = new SerialStruct(new InventoryItem("widget", 7), typeMap);
        SQLInput input = new DmdbSQLInput(typeMap, new Object[]{struct});

        InventoryItem item = assertInstanceOf(InventoryItem.class, input.readObject());
        assertEquals("INVENTORY_ITEM", item.getSQLTypeName());
        assertEquals("widget", item.name);
        assertEquals(7, item.quantity);
    }

    public static class InventoryItem implements SQLData {
        private String name;
        private int quantity;
        private String typeName = "INVENTORY_ITEM";

        public InventoryItem() {
        }

        public InventoryItem(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }

        @Override
        public String getSQLTypeName() {
            return typeName;
        }

        @Override
        public void readSQL(SQLInput input, String sqlTypeName) throws SQLException {
            typeName = sqlTypeName;
            name = input.readString();
            quantity = input.readInt();
        }

        @Override
        public void writeSQL(SQLOutput output) throws SQLException {
            output.writeString(name);
            output.writeInt(quantity);
        }
    }
}
