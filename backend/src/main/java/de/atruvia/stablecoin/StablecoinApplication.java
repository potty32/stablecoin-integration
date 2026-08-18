package de.atruvia.stablecoin;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class StablecoinApplication {

    public static void main(String[] args) {
        SpringApplication.run(StablecoinApplication.class, args);
    }

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Atruvia Stablecoin Integration API")
                        .version("1.0.0")
                        .description("MiCA-konforme Stablecoin-Zahlungsplattform (USDC/EURC) " +
                                "für B2B-Firmenkunden und B2C-Privatkunden der genossenschaftlichen FinanzGruppe.")
                        .contact(new Contact()
                                .name("Atruvia AG — Digital Banking")
                                .email("stablecoin-api@atruvia.de"))
                        .license(new License()
                                .name("Proprietary — Atruvia AG"))
                )
                .externalDocs(new ExternalDocumentation()
                        .description("MiCA-Compliance-Dokumentation")
                        .url("https://stablecoin.atruvia.de/docs/mica-compliance"));
    }
}
