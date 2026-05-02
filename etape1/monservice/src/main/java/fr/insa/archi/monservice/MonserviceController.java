package fr.insa.archi.monservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/monservice")
public class MonserviceController {

    @GetMapping("/echo/{nom}")
    public Map<String, String> echo(@PathVariable String nom) {
        return Map.of("message", "echo: " + nom);
    }

    @PostMapping("/hello")
    public Map<String, String> hello(@RequestBody Map<String, String> body) {
        String nom = body.getOrDefault("nom", "anonymous");
        return Map.of("message", "Hello " + nom);
    }
}
