/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;

@DgsComponent
public class JavaDgsComponent {
    @DgsQuery(field = "greeting")
    public String greeting() {
        return "hello";
    }

    @DgsQuery(field = "echo")
    public String echo(@InputArgument(name = "value") String value) {
        return value;
    }
}
