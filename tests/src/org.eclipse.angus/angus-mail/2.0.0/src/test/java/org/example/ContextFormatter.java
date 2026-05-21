/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.example;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public final class ContextFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        return record.getMessage();
    }
}
