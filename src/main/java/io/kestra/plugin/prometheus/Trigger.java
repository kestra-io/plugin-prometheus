package io.kestra.plugin.prometheus;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Wait for a Prometheus PromQL query to return results."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger when a Prometheus query returns results",
            full = true,
            code = """
                id: prometheus_trigger
                namespace: company.team
                tasks:
                  - id: each
                    type: io.kestra.plugin.core.flow.ForEach
                    values: "{{ trigger.metrics }}"
                    tasks:
                      - id: return
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ json(taskrun.value) }}"
                triggers:
                  - id: watch
                    type: io.kestra.plugin.prometheus.QueryTrigger
                    interval: "PT30S"
                    url: "http://localhost:9090"
                    query: "kestra_test_metric > 100"
                """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Query.Output> {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    private Property<String> username;

    private Property<String> password;

    private Property<String> time;

    @NotNull
    private Property<String> url;

    @NotNull
    private Property<String> query;

    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.NONE);

    private Property<Map<String, String>> headers;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        Query queryTask = Query.builder()
            .id(this.id)
            .type(Query.class.getName())
            .url(this.url)
            .query(this.query)
            .fetchType(this.fetchType)
            .time(this.time)
            .headers(this.headers)
            .username(this.username)
            .password(this.password)
            .build();

        Query.Output output = queryTask.run(runContext);
        logger.debug("Found '{}' results", output.getTotal());

        if (output.getTotal() == 0) {
            return Optional.empty();
        }

        return Optional.of(
            TriggerService.generateExecution(this, conditionContext, context, output)
        );
    }
}
