/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.parser.JSONLexerBase;
import com.alibaba.fastjson.parser.JSONScanner;

import java.util.Collection;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

public class JSONLexerBaseTest {
    @Test
    void scanFieldStringArrayInstantiatesRequestedCollectionType() {
        JSONLexerBase lexer = new JSONScanner("\"names\":[\"Bob\",\"Ada\"]}");
        try {
            Collection<String> names = lexer.scanFieldStringArray("\"names\":".toCharArray(), TreeSet.class);

            assertThat(names).isInstanceOf(TreeSet.class);
            assertThat(names).containsExactly("Ada", "Bob");
        } finally {
            lexer.close();
        }
    }
}
