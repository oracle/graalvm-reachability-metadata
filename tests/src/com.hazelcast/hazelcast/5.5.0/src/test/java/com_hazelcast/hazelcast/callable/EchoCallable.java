/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_hazelcast.hazelcast.callable;

import java.io.Serializable;
import java.util.concurrent.Callable;

public class EchoCallable implements Callable<String>, Serializable {

    private static final long serialVersionUID = -3325101595982916077L;

    private final String result;

    public EchoCallable(final String result) {
        this.result = result;
    }

    @Override
    public String call() throws Exception {
        return this.result;
    }
}
