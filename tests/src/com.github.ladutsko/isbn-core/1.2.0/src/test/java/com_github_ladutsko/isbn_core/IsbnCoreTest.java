/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ladutsko.isbn_core;

import com.github.ladutsko.isbn.ISBN;
import com.github.ladutsko.isbn.ISBNFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.fail;

class IsbnCoreTest {
    @Test
    void test() throws Exception {
        ISBN isbn = ISBN.parseIsbn("0131872486"); // or 978-0131872486
        // Valid isbn string
        ISBNFormat format = new ISBNFormat();
        System.out.println(format.format(isbn.getIsbn10())); // output: 0-13-187248-6
        System.out.println(format.format(isbn.getIsbn13())); // output: 978-0-13-187248-6
    }
}
