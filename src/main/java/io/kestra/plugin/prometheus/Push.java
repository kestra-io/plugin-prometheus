package io.kestra.plugin.prometheus;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Push metrics to Prometheus Pushgateway",
    description = "Send custom metrics to Prometheus via the Pushgateway"
)
@Plugin(
    examples = {
        @Example(
            title = "Push a custom metric",
            full = true,
            code = """
                id: push-metric
                namespace: company.team

                tasks:
                  - id: push
                    type: io.kestra.plugin.prometheus.Push
                    url: "http://localhost:9091"
                    job: "test_job"
                    instance: "test_instance"
                    metrics:
                      - name: "kestra_test_metric"
                        value: "123"
                        labels:
                          env: "test"
                          app: "kestra"
                """
        )
    }
)
public class Push extends AbstractPrometheusTask<Push.Output> {
    @NotNull
    @Schema(
        title = "Base URL",
        description = "Pushgateway URL."
    )
    @Builder.Default
    protected Property<String> url = Property.ofValue("http://localhost:9091");

    @NotNull
    @Schema(
        title = "Job name",
        description = "Prometheus job name."
    )
    private Property<String> job;

    @Schema(
        title = "Instance",
        description = "Optional instance label."
    )
    private Property<String> instance;

    @NotNull
    @Schema(
        title = "Metrics",
        description = "List of metrics to push."
    )
    private Property<List<Metric>> metrics;

    @Override
    protected HttpRequest buildRequest(RunContext runContext) throws Exception {
        String baseUrl = runContext.render(this.url).as(String.class).orElseThrow();
        String jobName = runContext.render(this.job).as(String.class).orElseThrow();

        String targetUrl = baseUrl + "/metrics/job/" + URLEncoder.encode(jobName, StandardCharsets.UTF_8);

        if (this.instance != null) {
            String instanceVal = runContext.render(this.instance).as(String.class).orElseThrow();
            targetUrl += "/instance/" + URLEncoder.encode(instanceVal, StandardCharsets.UTF_8);
        }

        List<Metric> renderedMetrics = runContext.render(this.metrics).asList(Metric.class);

        String body = renderedMetrics.stream()
            .map(this::formatMetric)
            .collect(Collectors.joining("\n", "", "\n"));

        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(targetUrl))
            .method("POST")
            .addHeader("Content-Type", "text/plain; version=0.0.4")
            .body(HttpRequest.StringRequestBody.builder().content(body).build());

        if (this.headers != null) {
            runContext.render(this.headers)
                .asMap(String.class, String.class)
                .forEach(requestBuilder::addHeader);
        }

        return requestBuilder.build();
    }

    private String formatMetric(Metric m) {
        String labels = (m.getLabels() != null && !m.getLabels().isEmpty())
            ? m.getLabels().entrySet().stream()
            .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
            .collect(Collectors.joining(",", "{", "}"))
            : "";

        return m.getName() + labels + " " + m.getValue();
    }

    @Override
    protected Output handleResponse(RunContext runContext, HttpResponse<String> response) {
        return Output.builder()
            .status("success")
            .code(response.getStatus().getCode())
            .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Metric {
        private String name;
        private String value;
        private Map<String, String> labels;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String status;
        private int code;
    }
}
