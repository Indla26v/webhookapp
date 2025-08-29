package com.example.WebhookApplication.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory; // <-- Import added
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // Replaced deprecated methods with the recommended requestFactory configuration
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(Duration.ofSeconds(30));
                    requestFactory.setReadTimeout(Duration.ofSeconds(30));
                    return requestFactory;
                })
                .interceptors(loggingInterceptor())
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            System.out.println("🌐 HTTP Request: " + request.getMethod() + " " + request.getURI());
            System.out.println("📤 Request Headers: " + request.getHeaders());

            if (body.length > 0) {
                System.out.println("📤 Request Body: " + new String(body));
            }

            var response = execution.execute(request, body);

            System.out.println("📥 Response Status: " + response.getStatusCode());
            System.out.println("📥 Response Headers: " + response.getHeaders());

            return response;
        };
    }
}