/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.transaction;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

public class TransactionManagerAdapter implements TransactionManager {

    @Override
    public void begin() {
    }

    @Override
    public void commit() throws HeuristicMixedException, HeuristicRollbackException, IllegalStateException,
            RollbackException, SecurityException, SystemException {
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public Transaction getTransaction() {
        return null;
    }

    @Override
    public void resume(final Transaction arg0) throws IllegalStateException {
    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException {
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException {
    }

    @Override
    public void setTransactionTimeout(final int arg0) {
    }

    @Override
    public Transaction suspend() {
        return null;
    }

}
