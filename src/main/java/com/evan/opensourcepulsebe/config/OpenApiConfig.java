package com.evan.opensourcepulsebe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openSourcePulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Open Source Pulse API")
                        .description("REST API for discovering, curating, and filtering beginner-friendly open-source GitHub issues (Good First Issues, Help Wanted).")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Open Source Pulse")
                                .url("https://github.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/").description("Default Server URL")
                ));
    }
}

