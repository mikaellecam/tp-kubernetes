package fr.insa.labotrack.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
public class AnalysisController {

    private final AnalysisResultRepository repository;
    private final SampleClient sampleClient;
    private final Random random = new Random();

    @Value("${analysis.simulated-latency-ms:0}")
    private long simulatedLatencyMs;

    public AnalysisController(AnalysisResultRepository repository, SampleClient sampleClient) {
        this.repository = repository;
        this.sampleClient = sampleClient;
    }

    @PostMapping("/analyze/{id}")
    public ResponseEntity<AnalysisResult> analyze(@PathVariable Long id) throws InterruptedException {
        if (simulatedLatencyMs > 0) {
            Thread.sleep(simulatedLatencyMs);
        }

        Map<String, Object> sample = sampleClient.fetch(id);
        if (sample == null) {
            return ResponseEntity.notFound().build();
        }

        AnalysisResult existing = repository.findBySampleId(id).orElse(null);
        AnalysisResult result = existing != null ? existing : new AnalysisResult();
        result.setSampleId(id);
        result.setParameter("glycemia");
        double value = 0.65 + random.nextDouble() * 0.65;
        result.setValue(Math.round(value * 100.0) / 100.0);
        result.setUnit("g/L");
        result.setInterpretation(interpret(result.getValue()));
        AnalysisResult saved = repository.save(result);

        sampleClient.markValidated(id);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/results/{sampleId}")
    public ResponseEntity<AnalysisResult> get(@PathVariable Long sampleId) {
        return repository.findBySampleId(sampleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/results")
    public List<AnalysisResult> list() {
        return repository.findAll();
    }

    private static String interpret(double v) {
        if (v < 0.74) return "low";
        if (v > 1.06) return "high";
        return "normal";
    }
}
