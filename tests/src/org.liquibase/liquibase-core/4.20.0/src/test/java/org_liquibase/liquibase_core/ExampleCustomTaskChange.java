/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Scope;
import liquibase.change.custom.CustomTaskChange;
import liquibase.change.custom.CustomTaskRollback;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public class ExampleCustomTaskChange implements CustomTaskChange, CustomTaskRollback {

    private String helloTo;

    @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
    private ResourceAccessor resourceAccessor;


    public String getHelloTo() {
        return helloTo;
    }

    public void setHelloTo(String helloTo) {
        this.helloTo = helloTo;
    }

    @Override
    public void execute(Database database) {
        Scope.getCurrentScope().getLog(getClass()).info("Hello " + getHelloTo());
    }

    @Override
    public void rollback(Database database) {
        Scope.getCurrentScope().getLog(getClass()).info("Goodbye " + getHelloTo());
    }

    @Override
    public String getConfirmationMessage() {
        return "Said Hello";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
