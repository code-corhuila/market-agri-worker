package co.edu.corhuila.marketagri.worker.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Scans the app and the adapters; the core carries no Spring annotation,
 * so its types are instantiated explicitly in {@link WorkerConfiguration}.
 */
@SpringBootApplication(scanBasePackages = {
        "co.edu.corhuila.marketagri.worker.app",
        "co.edu.corhuila.marketagri.worker.adapter"})
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
