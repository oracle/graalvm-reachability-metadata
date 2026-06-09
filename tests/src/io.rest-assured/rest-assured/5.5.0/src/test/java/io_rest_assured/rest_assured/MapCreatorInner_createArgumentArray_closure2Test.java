/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.util.Map;

import io.restassured.internal.MapCreator;
import io.restassured.internal.MapCreator.CollisionStrategy;
import org.codehaus.groovy.runtime.metaclass.DefaultMetaClassInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapCreatorInner_createArgumentArray_closure2Test {
    @Test
    void publicParameterNameApiAppendsAdditionalNameValuePairs() {
        Object[] additionalNameValuePairs = {"one", "second", "two", "third", "three"};
        DefaultMetaClassInfo.setWithoutCustomMetaclassCreationHandle(true);

        try {
            Map<String, Object> map = MapCreator.createMapFromParams(
                    CollisionStrategy.OVERWRITE,
                    "first",
                    additionalNameValuePairs);

            assertEquals("one", map.get("first"));
            assertEquals("two", map.get("second"));
            assertEquals("three", map.get("third"));
        } finally {
            DefaultMetaClassInfo.setWithoutCustomMetaclassCreationHandle(false);
        }
    }
}
