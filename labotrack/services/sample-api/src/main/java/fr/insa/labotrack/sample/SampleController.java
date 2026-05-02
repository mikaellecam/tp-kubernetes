package fr.insa.labotrack.sample;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/samples")
public class SampleController {

    private final SampleRepository repository;

    public SampleController(SampleRepository repository) {
        this.repository = repository;
    }

    public record CreateRequest(@NotBlank String patient,
                                @NotBlank String examType,
                                @NotBlank String sampleType) {}

    public record StatusUpdate(Sample.Status status) {}

    @PostMapping
    public Sample create(@RequestBody CreateRequest req) {
        Sample s = new Sample();
        s.setPatient(req.patient());
        s.setExamType(req.examType());
        s.setSampleType(req.sampleType());
        return repository.save(s);
    }

    @GetMapping
    public List<Sample> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sample> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Sample> updateStatus(@PathVariable Long id, @RequestBody StatusUpdate update) {
        return repository.findById(id)
                .map(s -> {
                    s.setStatus(update.status());
                    return ResponseEntity.ok(repository.save(s));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
