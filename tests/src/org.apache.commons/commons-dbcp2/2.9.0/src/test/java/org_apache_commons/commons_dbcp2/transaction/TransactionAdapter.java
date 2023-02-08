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
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;


public class TransactionAdapter implements Transaction {

    @Override
    public void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException, SecurityException, SystemException {
    }

    @Override
    public boolean delistResource(final XAResource arg0, final int arg1) throws IllegalStateException {
        return false;
    }

    @Override
    public boolean enlistResource(final XAResource arg0) throws IllegalStateException {
        return false;
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public void registerSynchronization(final Synchronization arg0) throws IllegalStateException {
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException {
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException {
    }

}
