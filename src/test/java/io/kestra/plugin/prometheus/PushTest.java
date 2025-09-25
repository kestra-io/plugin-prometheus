package io.kestra.plugin.prometheus;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class PushTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void testPushAndQuery() throws Exception {
        Push pushTask = Push.builder()
            .url(Property.ofValue("http://localhost:9091"))
            .job(Property.ofValue("test_job"))
            .instance(Property.ofValue("test_instance"))
            .metrics(Property.ofValue(List.of(
                Push.Metric.builder()
                    .name("kestra_test_metric")
                    .value("123")
                    .labels(Map.of("env", "test", "app", "kestra"))
                    .build()
            )))
            .build();

        Push.Output pushOutput = pushTask.run(runContextFactory.of());
        assertThat(pushOutput.getStatus(), is("success"));

        Thread.sleep(2000);

        Query queryTask = Query.builder()
            .url(Property.ofValue("http://localhost:9090"))
            .query(Property.ofValue("kestra_test_metric"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        Query.Output queryOutput = queryTask.run(runContextFactory.of());

        assertThat(queryOutput.getResultType(), is("vector"));
        assertThat(queryOutput.getTotal(), greaterThan(0));

        Query.PrometheusMetric metric = queryOutput.getMetrics().getFirst();
        String metricString = metric.getLabels().toString() + " " + metric.getValue();

        assertThat(metric.getValue(), is("123"));
        assertThat(metricString, containsString("exported_job=test_job"));
        assertThat(metricString, containsString("exported_instance=test_instance"));
        assertThat(metricString, containsString("app=kestra"));
        assertThat(metricString, containsString("env=test"));
        assertThat(metricString, containsString("123"));
    }
}
