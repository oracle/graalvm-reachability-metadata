/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_validator.commons_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.apache.commons.validator.Field;
import org.apache.commons.validator.Form;
import org.apache.commons.validator.FormSet;
import org.apache.commons.validator.Validator;
import org.apache.commons.validator.ValidatorAction;
import org.apache.commons.validator.ValidatorResult;
import org.apache.commons.validator.ValidatorResults;
import org.apache.commons.validator.ValidatorResources;
import org.junit.jupiter.api.Test;

public class ValidatorActionTest {
    private static final String ACTION_NAME = "dateValue";
    private static final String FIELD_NAME = "invoiceDate";
    private static final String FORM_NAME = "invoiceForm";

    @Test
    void validatesFieldWithInstanceMethodLoadedFromValidatorAction() throws Exception {
        ValidatorResources resources = resourcesWithDateAction();
        Validator validator = new Validator(resources, FORM_NAME);
        validator.setParameter(String.class.getName(), "1/1/16");
        validator.setParameter(Locale.class.getName(), Locale.US);

        ValidatorResults results = validator.validate();

        ValidatorResult fieldResult = results.getValidatorResult(FIELD_NAME);
        assertThat(fieldResult).isNotNull();
        assertThat(fieldResult.isValid(ACTION_NAME)).isTrue();
        assertThat(results.getResultValueMap()).containsKey(FIELD_NAME);
    }

    @Test
    void loadsJavascriptThroughContextClassLoaderResource() {
        ValidatorAction action = actionWithJavascriptResource("contextResource");
        action.setJsFunction("/org/apache/commons/validator/resources/validator_1_4_0.dtd");
        ValidatorResources resources = new ValidatorResources();

        resources.addValidatorAction(action);

        assertThat(action.getJavascript()).isNotBlank();
    }

    @Test
    void loadsJavascriptThroughValidatorActionClassResourceFallback() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            ValidatorAction action = actionWithJavascriptResource("classResource");
            action.setJsFunction("/resources/validator_1_4_0.dtd");
            ValidatorResources resources = new ValidatorResources();

            resources.addValidatorAction(action);

            assertThat(action.getJavascript()).isNotBlank();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static ValidatorResources resourcesWithDateAction() {
        ValidatorResources resources = new ValidatorResources();
        resources.addValidatorAction(dateAction());
        resources.addFormSet(formSet());
        resources.process();
        return resources;
    }

    private static ValidatorAction dateAction() {
        ValidatorAction action = new ValidatorAction();
        action.setName(ACTION_NAME);
        action.setClassname("org.apache.commons.validator.routines.DateValidator");
        action.setMethod("validate");
        action.setMethodParams(String.class.getName() + "," + Locale.class.getName());
        action.setJavascript("function validateDateValue(form) { return true; }");
        return action;
    }

    private static FormSet formSet() {
        FormSet formSet = new FormSet();
        formSet.addForm(form());
        return formSet;
    }

    private static Form form() {
        Form form = new Form();
        form.setName(FORM_NAME);
        form.addField(dateField());
        return form;
    }

    private static Field dateField() {
        Field field = new Field();
        field.setProperty(FIELD_NAME);
        field.setDepends(ACTION_NAME);
        return field;
    }

    private static ValidatorAction actionWithJavascriptResource(String name) {
        ValidatorAction action = new ValidatorAction();
        action.setName(name);
        return action;
    }

}
