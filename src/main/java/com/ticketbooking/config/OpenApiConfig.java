package com.ticketbooking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketBookingOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Ticket Booking & Reservation System API")
                .description("High-concurrency ticket reservations, event catalog, shows, and payments API.")
                .version("v1.0.0")
                .contact(new Contact().name("Ticket Booking Engineering"))
                .license(new License().name("Apache 2.0")));
    }
}
