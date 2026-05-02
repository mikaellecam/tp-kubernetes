package fr.insa.labotrack.frontend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class BackendClient {

    private final WebClient sampleClient;
    private final WebClient analysisClient;

    public BackendClient(WebClient.Builder builder,
                         @Value("${labotrack.sample-api.base-url:http://sample-api:8080}") String sampleBase,
                         @Value("${labotrack.analysis-api.base-url:http://analysis-api:8080}") String analysisBase) {
        this.sampleClient = builder.clone().baseUrl(sampleBase).build();
        this.analysisClient = builder.clone().baseUrl(analysisBase).build();
    }

    public List<Map<String, Object>> listSamples() {
        return sampleClient.get().uri("/samples")
                .retrieve()
                .bodyToFlux(Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();
    }

    public Map<String, Object> createSample(String patient, String examType, String sampleType) {
        return sampleClient.post().uri("/samples")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("patient", patient, "examType", examType, "sampleType", sampleType))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> analyze(Long id) {
        return analysisClient.post().uri("/analyze/{id}", id)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    public Map<String, Object> result(Long sampleId) {
        try {
            return analysisClient.get().uri("/results/{id}", sampleId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            return null;
        }
    }
}
