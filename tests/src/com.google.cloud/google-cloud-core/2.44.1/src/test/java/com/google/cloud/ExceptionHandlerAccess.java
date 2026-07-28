/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.cloud;

import java.util.concurrent.Callable;

public final class ExceptionHandlerAccess {
    private ExceptionHandlerAccess() {
    }

    public static void verifyCaller(ExceptionHandler handler, Callable<?> callable) {
        handler.verifyCaller(callable);
    }
}
