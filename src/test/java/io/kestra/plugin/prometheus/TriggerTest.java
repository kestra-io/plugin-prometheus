package io.kestra.plugin.prometheus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class TriggerTest {

    @SuppressWarnings("unchecked")
    @Test
    @EvaluateTrigger(flow = "flows/prometheus-listen.yml", triggerId = "watch")
    void run(Optional<Execution> optionalExecution) {
        assertThat(optionalExecution.isPresent(), is(true));

        Execution execution = optionalExecution.get();
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) execution.getTrigger().getVariables().get("metrics");

        assertThat(metrics.size(), greaterThan(0));
        assertThat(metrics.getFirst().get("value"), is("150"));
        assertThat(metrics.getFirst().get("labels").toString(), containsString("trigger_test"));
    }
}
