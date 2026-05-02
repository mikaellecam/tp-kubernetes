package fr.insa.labotrack.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class SampleClient {

    private final WebClient client;

    public SampleClient(WebClient.Builder builder,
                        @Value("${labotrack.sample-api.base-url:http://sample-api:8080}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public Map<String, Object> fetch(Long id) {
        return client.get().uri("/samples/{id}", id)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public void markValidated(Long id) {
        client.patch().uri("/samples/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "VALIDATED"))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
