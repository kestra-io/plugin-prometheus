package io.kestra.plugin.prometheus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SuperBuilder
@EqualsAndHashCode
@ToString
@Getter
@NoArgsConstructor
@Schema(
    title = "Query Prometheus metrics using PromQL",
    description = "Execute PromQL queries against Prometheus and return structured metric data"
)
@Plugin(
    examples = {
        @Example(
            title = "Query CPU usage",
            full = true,
            code = """
                id: prometheus-up-query
                namespace: company.team

                tasks:
                  - id: check_up
                    type: io.kestra.plugin.prometheus.Query
                    url: "http://localhost:9090"
                    query: "up"
                """
        ),
        @Example(
            title = "Query a pushed custom metric",
            full = true,
            code = """
                id: query-custom-metric
                namespace: io.kestra.tests

                tasks:
                  - id: query_metric
                    type: io.kestra.plugin.prometheus.Query
                    url: "http://localhost:9090"
                    query: "kestra_test_metric"
                """
        )
    }
)
public class Query extends AbstractPrometheusTask<Query.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @NotNull
    @Schema(
        title = "Base URL",
        description = "Prometheus server URL."
    )
    @Builder.Default
    protected Property<String> url = Property.ofValue("http://localhost:9090");

    @NotNull
    @Schema(
        title = "PromQL query",
        description = "Prometheus query language expression"
    )
    private Property<String> query;

    @Schema(
        title = "Query time",
        description = "Time for the query (RFC3339 or Unix timestamp). Defaults to current time"
    )
    private Property<String> time;

    @Schema(
        title = "The way you want to store the data.",
        description = "FETCH_ONE output the first row, "
            + "FETCH output all the rows, "
            + "STORE store all rows in a file, "
            + "NONE do nothing."
    )
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.NONE);

    @Override
    protected HttpRequest buildRequest(RunContext runContext) throws Exception {
        String renderedUrl = runContext.render(this.url).as(String.class).orElse("http://localhost:9090");
        String renderedQuery = runContext.render(this.query).as(String.class).orElseThrow();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", renderedQuery);

        runContext.render(this.time).as(String.class).ifPresent(t -> params.put("time", t));

        String queryString = params.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .reduce((a, b) -> a + "&" + b)
            .orElse("");

        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(renderedUrl + "/api/v1/query?" + queryString))
            .method("GET")
            .addHeader("Content-Type", "application/json");

        if (this.headers != null) {
            runContext.render(this.headers)
                .asMap(String.class, String.class)
                .forEach(requestBuilder::addHeader);
        }

        return requestBuilder.build();
    }

    @Override
    protected Output handleResponse(RunContext runContext, HttpResponse<String> response) throws Exception {
        JsonNode responseJson = MAPPER.readTree(response.getBody());

        if (!"success".equals(responseJson.get("status").asText())) {
            throw new RuntimeException("Prometheus query failed: " + responseJson.get("error").asText());
        }

        JsonNode data = responseJson.get("data");
        ResultType resultType = ResultType.fromString(data.get("resultType").asText());
        JsonNode result = data.get("result");

        List<PrometheusMetric> metrics = switch (resultType) {
            case VECTOR -> parseVector(result);
            case MATRIX -> parseMatrix(result);
            case SCALAR -> parseScalar(result);
            case STRING -> parseString(result);
        };

        return handleFetchType(runContext, metrics, resultType);
    }

    private Output handleFetchType(RunContext runContext, List<PrometheusMetric> metrics, ResultType resultType) throws Exception {
        FetchType type = runContext.render(this.fetchType).as(FetchType.class).orElseThrow();

        Output.OutputBuilder builder = Output.builder()
            .resultType(resultType.getValue())
            .total(metrics.size());

        switch (type) {
            case FETCH -> builder.metrics(metrics).size(metrics.size());
            case FETCH_ONE -> {
                PrometheusMetric first = metrics.isEmpty() ? null : metrics.getFirst();
                builder.metric(first).size(first != null ? 1 : 0);
            }
            case STORE -> {
                URI uri = storeResults(runContext, metrics);
                builder.uri(uri).size(metrics.size());
            }
            case NONE -> builder.size(metrics.size());
        }

        return builder.build();
    }

    private URI storeResults(RunContext runContext, List<PrometheusMetric> metrics) throws Exception {
        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        try (var output = new BufferedWriter(new FileWriter(tempFile), FileSerde.BUFFER_SIZE)) {
            Flux<PrometheusMetric> recordFlux = Flux.fromIterable(metrics);
            FileSerde.writeAll(output, recordFlux).block();
            return runContext.storage().putFile(tempFile);
        }
    }

    private List<PrometheusMetric> parseVector(JsonNode result) {
        List<PrometheusMetric> metrics = new ArrayList<>();
        for (JsonNode metricNode : result) {
            Map<String, String> labels = extractLabels(metricNode.get("metric"));
            JsonNode value = metricNode.get("value");
            if (value != null && value.isArray() && value.size() > 1) {
                metrics.add(PrometheusMetric.builder()
                    .labels(labels)
                    .timestamp(value.get(0).asDouble())
                    .value(value.get(1).asText())
                    .build());
            }
        }
        return metrics;
    }

    private List<PrometheusMetric> parseMatrix(JsonNode result) {
        List<PrometheusMetric> metrics = new ArrayList<>();
        for (JsonNode metricNode : result) {
            Map<String, String> labels = extractLabels(metricNode.get("metric"));
            JsonNode values = metricNode.get("values");
            if (values != null) {
                for (JsonNode valueNode : values) {
                    if (valueNode.isArray() && valueNode.size() > 1) {
                        metrics.add(PrometheusMetric.builder()
                            .labels(labels)
                            .timestamp(valueNode.get(0).asDouble())
                            .value(valueNode.get(1).asText())
                            .build());
                    }
                }
            }
        }
        return metrics;
    }

    private List<PrometheusMetric> parseScalar(JsonNode result) {
        List<PrometheusMetric> metrics = new ArrayList<>();
        if (result != null && result.isArray() && result.size() > 1) {
            metrics.add(PrometheusMetric.builder()
                .labels(Map.of())
                .timestamp(result.get(0).asDouble())
                .value(result.get(1).asText())
                .build());
        }
        return metrics;
    }

    private List<PrometheusMetric> parseString(JsonNode result) {
        List<PrometheusMetric> metrics = new ArrayList<>();
        if (result != null && result.isArray() && result.size() > 1) {
            metrics.add(PrometheusMetric.builder()
                .labels(Map.of())
                .timestamp(result.get(0).asDouble())
                .value(result.get(1).asText())
                .build());
        }
        return metrics;
    }

    private Map<String, String> extractLabels(JsonNode metric) {
        Map<String, String> labels = new HashMap<>();
        if (metric != null) {
            metric.fields().forEachRemaining(e -> labels.put(e.getKey(), e.getValue().asText()));
        }
        return labels;
    }

    @Getter
    public enum ResultType {
        VECTOR("vector"),
        MATRIX("matrix"),
        SCALAR("scalar"),
        STRING("string");

        private final String value;

        ResultType(String value) { this.value = value; }

        public static ResultType fromString(String type) {
            return Arrays.stream(values())
                .filter(rt -> rt.value.equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid Prometheus result type: " + type));
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The number of rows fetched."
        )
        private Integer size;

        @Schema(
            title = "Total results",
            description = "Total number of metric results"
        )
        private int total;

        @Schema(
            title = "Prometheus metrics",
            description = "List of metrics returned by the query"
        )
        private List<PrometheusMetric> metrics;

        @Schema(
            title = "first row of fetched data.",
            description = "Only populated if using `fetchType=FETCH_ONE`."
        )
        private PrometheusMetric metric;

        @Schema(
            title = "The URI of the stored data.",
            description = "Only populated if using `fetchType=STORE`."
        )
        private URI uri;

        @Schema(
            title = "Result type",
            description = "Type of Prometheus result (vector, matrix, scalar, string)"
        )
        private String resultType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrometheusMetric {
        @JsonProperty("labels")
        private Map<String, String> labels;

        @JsonProperty("timestamp")
        private double timestamp;

        @JsonProperty("value")
        private String value;
    }
}
