package fr.insa.labotrack.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final BackendClient backend;

    public HomeController(BackendClient backend) {
        this.backend = backend;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Map<String, Object>> samples = backend.listSamples();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (samples != null) {
            for (Map<String, Object> s : samples) {
                Map<String, Object> row = new java.util.HashMap<>(s);
                Object idObj = s.get("id");
                if (idObj instanceof Number) {
                    row.put("result", backend.result(((Number) idObj).longValue()));
                }
                rows.add(row);
            }
        }
        model.addAttribute("samples", rows);
        return "index";
    }

    @PostMapping("/samples")
    public String createSample(@RequestParam String patient,
                               @RequestParam String examType,
                               @RequestParam String sampleType) {
        backend.createSample(patient, examType, sampleType);
        return "redirect:/";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam Long id) {
        backend.analyze(id);
        return "redirect:/";
    }
}
