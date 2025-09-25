package io.kestra.plugin.prometheus;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@KestraTest
public class QueryTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        Query task = Query.builder()
            .url(Property.ofValue("http://localhost:9090"))
            .query(Property.ofValue("up"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        Query.Output output = task.run(runContextFactory.of());

        assertThat("vector", is(output.getResultType()));
        assertThat(output.getTotal(), greaterThan( 0));

        boolean allUp = output.getMetrics().stream().allMatch(m -> "1".equals(m.getValue()));
        assertThat(allUp, is(true));
    }
}
