package com.example.WebhookApplication.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class StartupService {

    private static final Logger logger = LoggerFactory.getLogger(StartupService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SqlSolverService sqlSolverService;

    // Inject configuration values from application.properties
    @Value("${app.webhook.generate-url}")
    private String generateWebhookUrl;

    @Value("${app.user.name}")
    private String userName;

    @Value("${app.user.regNo}")
    private String userRegNo;

    @Value("${app.user.email}")
    private String userEmail;

    @Autowired
    public StartupService(RestTemplate restTemplate, ObjectMapper objectMapper, SqlSolverService sqlSolverService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sqlSolverService = sqlSolverService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void executeOnStartup() {
        logger.info("Application started - Beginning webhook process...");

        try {
            // Step 1: Generate webhook
            WebhookResponse webhookData = generateWebhook();

            if (webhookData == null) {
                logger.error("Failed to generate webhook - stopping process");
                return;
            }

            logger.info("Webhook generated successfully");

            // Step 2: Solve SQL problem based on registration number
            String sqlSolution = solveSqlProblem();

            if (sqlSolution == null) {
                logger.error("Failed to solve SQL problem - stopping process");
                return;
            }

            logger.info("SQL solution prepared: {}", sqlSolution);

            // Step 3: Submit solution to the DYNAMIC webhook URL (CRITICAL FIX)
            boolean submitted = submitSolution(webhookData.getWebhook(), webhookData.getAccessToken(), sqlSolution);

            if (submitted) {
                logger.info("Solution submitted successfully!");
            } else {
                logger.error("Failed to submit solution");
            }

        } catch (Exception e) {
            logger.error("Error during webhook process: ", e);
        }
    }

    private WebhookResponse generateWebhook() {
        try {
            // Prepare request body using injected configuration values
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("name", userName);
            requestBody.put("regNo", userRegNo);
            requestBody.put("email", userEmail);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            logger.info("Sending POST request to generate webhook...");

            // Make the request using injected URL
            ResponseEntity<String> response = restTemplate.postForEntity(
                    generateWebhookUrl, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Parse response
                JsonNode responseJson = objectMapper.readTree(response.getBody());

                // Enhanced logging for debugging authentication issues
                logger.info("✅ Webhook generation successful!");
                logger.info("📋 Full response: {}", response.getBody());

                String webhook = responseJson.get("webhook").asText();
                String accessToken = responseJson.get("accessToken").asText();

                logger.info("🔗 Received webhook: {}", webhook);
                logger.info("🔑 Received access token length: {} characters", accessToken.length());
                logger.info("🔑 Access token format check: {}",
                        accessToken.startsWith("eyJ") ? "Looks like JWT" : "Not JWT format");

                // Validate token format
                if (accessToken.length() < 10) {
                    logger.warn("⚠️ Access token seems too short - this may cause authentication issues");
                }

                return new WebhookResponse(webhook, accessToken);
            } else {
                logger.error("Webhook generation failed with status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            logger.error("Error generating webhook: ", e);
            return null;
        }
    }

    private String solveSqlProblem() {
        return sqlSolverService.getSolutionForRegistrationNumber(userRegNo);
    }

    private boolean submitSolution(String webhookUrl, String accessToken, String finalQuery) {
        try {
            logger.info("=== SUBMITTING SOLUTION ===");
            logger.info("Webhook URL: {}", webhookUrl);
            logger.info("Access Token (first 20 chars): {}...",
                    accessToken != null && accessToken.length() > 20 ?
                            accessToken.substring(0, 20) : accessToken);
            logger.info("Final Query length: {} characters", finalQuery != null ? finalQuery.length() : 0);

            // Validate inputs
            if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
                logger.error("Webhook URL is null or empty");
                return false;
            }

            if (accessToken == null || accessToken.trim().isEmpty()) {
                logger.error("Access token is null or empty");
                return false;
            }

            if (finalQuery == null || finalQuery.trim().isEmpty()) {
                logger.error("Final query is null or empty");
                return false;
            }

            // Prepare request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("finalQuery", finalQuery.trim());

            // Set headers with JWT token - CRITICAL AUTHENTICATION STEP
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Clean the access token (remove any extra whitespace/newlines)
            String cleanToken = accessToken.trim();

            // Set Bearer token - THIS IS THE CRITICAL LINE FOR AUTHENTICATION
            headers.setBearerAuth(cleanToken);

            // Log the Authorization header for debugging (without exposing full token)
            String authHeader = headers.getFirst("Authorization");
            if (authHeader != null) {
                logger.info("Authorization header set: {}...",
                        authHeader.length() > 25 ? authHeader.substring(0, 25) : authHeader);
            } else {
                logger.error("Authorization header is NULL - this will cause 401 error");
            }

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            logger.info("Making POST request to webhook URL: {}", webhookUrl);
            logger.info("Request body: {}", requestBody);

            // CRITICAL FIX: Use the dynamic webhookUrl instead of hardcoded URL
            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhookUrl, entity, String.class);

            logger.info("✅ Submit response status: {}", response.getStatusCode());
            logger.info("✅ Submit response body: {}", response.getBody());

            return response.getStatusCode() == HttpStatus.OK;

        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            logger.error("🚨 401 UNAUTHORIZED ERROR - Authentication failed!");
            logger.error("This means the JWT token is invalid, malformed, or missing");
            logger.error("Response body: {}", e.getResponseBodyAsString());
            logger.error("Response headers: {}", e.getResponseHeaders());
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            logger.error("🚨 HTTP Client Error: {} - {}", e.getStatusCode(), e.getStatusText());
            logger.error("Response body: {}", e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            logger.error("🚨 Unexpected error submitting solution: ", e);
            return false;
        }
    }

    // Inner class to hold webhook response data
    private static class WebhookResponse {
        private final String webhook;
        private final String accessToken;

        public WebhookResponse(String webhook, String accessToken) {
            this.webhook = webhook;
            this.accessToken = accessToken;
        }

        public String getWebhook() {
            return webhook;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }
}