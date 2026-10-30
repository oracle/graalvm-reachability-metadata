/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    private final List<String> events = new ArrayList<>();

    private boolean failCommit;

    List<String> getEvents() {
        return List.copyOf(this.events);
    }

    void setFailCommit(boolean failCommit) {
        this.failCommit = failCommit;
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        this.events.add("begin:" + determineTimeout(definition));
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        this.events.add("commit");
        if (this.failCommit) {
            throw new TransactionSystemException("Commit rejected by transaction resource");
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        this.events.add("rollback");
    }
}
