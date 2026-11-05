/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.codec.Codec;
import org.jdbi.v3.core.codec.CodecFactory;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.qualifier.QualifiedType;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's codec subsystem for storing and loading application money values.
public class MoneyCodecTest {
    @Test
    void storesAndLoadsMoneyThroughOneApplicationCodec() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:money_codec");
        jdbi.registerCodecFactory(CodecFactory.forSingleCodec(
                QualifiedType.of(Money.class), new MoneyCodec()));

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE product_price (
                        sku VARCHAR(32) PRIMARY KEY,
                        price VARCHAR(32) NOT NULL
                    )
                    """);
            handle.createUpdate("""
                    INSERT INTO product_price (sku, price)
                    VALUES (:sku, :price)
                    """)
                    .bind("sku", "KB-01")
                    .bind("price", new Money("EUR", 4900))
                    .execute();

            Money price = handle.createQuery("""
                    SELECT price
                    FROM product_price
                    WHERE sku = :sku
                    """)
                    .bind("sku", "KB-01")
                    .mapTo(Money.class)
                    .one();

            assertEquals(new Money("EUR", 4900), price);
        });
    }

    private record Money(String currency, int cents) {
        private String asDatabaseValue() {
            return currency + ":" + cents;
        }

        private static Money fromDatabaseValue(String value) {
            String[] parts = value.split(":", -1);
            return new Money(parts[0], Integer.parseInt(parts[1]));
        }
    }

    private static final class MoneyCodec implements Codec<Money> {
        @Override
        public ColumnMapper<Money> getColumnMapper() {
            return new MoneyColumnMapper();
        }

        @Override
        public Function<Money, Argument> getArgumentFunction() {
            return money -> (position, statement, context) ->
                    statement.setString(position, money.asDatabaseValue());
        }
    }

    private static final class MoneyColumnMapper implements ColumnMapper<Money> {
        @Override
        public Money map(ResultSet resultSet, int columnNumber, StatementContext context)
                throws SQLException {
            return Money.fromDatabaseValue(resultSet.getString(columnNumber));
        }
    }
}
