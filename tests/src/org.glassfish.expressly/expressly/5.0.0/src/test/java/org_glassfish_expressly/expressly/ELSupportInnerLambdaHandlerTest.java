/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_expressly.expressly;

import java.lang.reflect.Method;
import java.util.List;

import org.glassfish.expressly.ExpressionFactoryImpl;
import org.glassfish.expressly.lang.ELSupport;
import org.junit.jupiter.api.Test;

import jakarta.el.ELContext;
import jakarta.el.LambdaExpression;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import static org.assertj.core.api.Assertions.assertThat;

public class ELSupportInnerLambdaHandlerTest {

    @Test
    void delegatesNonAbstractMethodInvocationToReceiver() throws Throwable {
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();
        ELContext elContext = new StandardELContext(expressionFactory);
        ValueExpression valueExpression = expressionFactory.createValueExpression("unused", String.class);
        LambdaExpression lambdaExpression = new LambdaExpression(List.of(), valueExpression);
        ELSupport.LambdaHandler lambdaHandler = new ELSupport.LambdaHandler(elContext, lambdaExpression);
        Method greetingMethod = Greeting.class.getMethod("greeting", String.class);

        Object greeting = lambdaHandler.invoke(new GreetingReceiver(), greetingMethod, new Object[] {"Expressly"});

        assertThat(greeting).isEqualTo("Hello, Expressly");
    }

    public interface Greeting {
        default String greeting(String name) {
            return "Hello, " + name;
        }
    }

    public static final class GreetingReceiver implements Greeting {
    }
}
