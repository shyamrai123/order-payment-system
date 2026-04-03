package com.example.orderpayment.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Payment System API")
                        .version("1.0.0")
                        .description("""
                                Event-driven Order & Payment system using Apache Kafka.
                                
                                Flow: POST /api/orders → Kafka order-topic → Payment Consumer
                                → payment-topic or payment-failed-topic → Notification Consumer
                                """)
                        .contact(new Contact()
                                .name("Platform Team")
                                .email("platform@example.com")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
                .components(new Components()
                        .addSecuritySchemes("basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")));
    }
}
