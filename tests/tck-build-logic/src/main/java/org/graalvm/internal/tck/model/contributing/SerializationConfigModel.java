package org.graalvm.internal.tck.model.contributing;


import java.util.List;

public record SerializationConfigModel(
        List<Object> types,
        List<Object> proxies,
        List<Object> lambdaCapturingTypes
) {
}
