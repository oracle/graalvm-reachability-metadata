/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_fastjson2.fastjson2;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.SymbolTable;
import org.junit.jupiter.api.Test;

public class Fastjson2SymbolTableTest {
    @Test
    void createsSortedUniqueSymbolTables() {
        SymbolTable symbols = JSONB.symbolTable("status", "id", "name", "id");

        assertThat(symbols.size()).isEqualTo(3);
        assertThat(symbols.getName(1)).isEqualTo("id");
        assertThat(symbols.getName(2)).isEqualTo("name");
        assertThat(symbols.getName(3)).isEqualTo("status");
        assertThat(symbols.getOrdinal("id")).isEqualTo(1);
        assertThat(symbols.getOrdinal("name")).isEqualTo(2);
        assertThat(symbols.getOrdinal("status")).isEqualTo(3);
    }

    @Test
    void resolvesNamesAndOrdinalsFromSymbolHashes() {
        SymbolTable symbols = new SymbolTable("customer", "items", "total");
        long customerHash = symbols.getHashCode(symbols.getOrdinal("customer"));
        long totalHash = symbols.getHashCode(symbols.getOrdinal("total"));

        assertThat(symbols.getNameByHashCode(customerHash)).isEqualTo("customer");
        assertThat(symbols.getOrdinalByHashCode(customerHash)).isEqualTo(symbols.getOrdinal("customer"));
        assertThat(symbols.getNameByHashCode(totalHash)).isEqualTo("total");
        assertThat(symbols.getOrdinalByHashCode(totalHash)).isEqualTo(symbols.getOrdinal("total"));
        assertThat(symbols.getNameByHashCode(0L)).isNull();
        assertThat(symbols.getOrdinalByHashCode(0L)).isEqualTo(-1);
        assertThat(symbols.getOrdinal("missing")).isEqualTo(-1);
    }

    @Test
    void computesStableAggregateSymbolHash() {
        SymbolTable first = new SymbolTable("zeta", "alpha", "beta");
        SymbolTable second = new SymbolTable("beta", "zeta", "alpha");
        SymbolTable different = new SymbolTable("alpha", "beta", "gamma");

        assertThat(first.hashCode64()).isEqualTo(second.hashCode64());
        assertThat(first.hashCode64()).isNotEqualTo(different.hashCode64());
        assertThat(first.hashCode64()).isNotZero();
    }
}
