package de.atruvia.stablecoin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA-Routing-Fix: Angular-Deep-Links (z.B. /b2b/transfers) lösen im
 * Single-Container-Deployment einen 404 aus, weil Spring Boot keine
 * Route dafür kennt. Dieser ResourceHandler leitet alle nicht gefundenen
 * Pfade auf index.html weiter — Angular übernimmt dann das Client-Routing.
 *
 * Ausgenommen: /api/**, /actuator/**, /v3/** (OpenAPI) — diese landen
 * weiterhin beim Spring-Controller.
 */
@Configuration
public class SpaRoutingConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath,
                                                   Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        // Existierende statische Datei (JS, CSS, Assets) direkt ausliefern
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Alles andere → index.html (Angular übernimmt Client-Routing)
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
