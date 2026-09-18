package com.example.demo.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class NeonRestoreService {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String projectId;
    private final String branchId;

    public NeonRestoreService(
            @Value("${neon.api.key}") String apiKey,
            @Value("${neon.project.id}") String projectId,
            @Value("${neon.branch.id}") String branchId) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.projectId = projectId;
        this.branchId = branchId;
    }

    public ResponseEntity<String> restore(String pointInTime) {
        validateConfiguration();

        String url = "https://console.neon.tech/api/v2/projects/"
                + projectId + "/branches/" + branchId + "/restore";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(
                Map.of("restore_to_point_in_time", pointInTime), headers);

        try {
            return restTemplate.postForEntity(url, request, String.class);
        } catch (RestClientResponseException exception) {
            throw new NeonRestoreException(
                    "Neon devolvio HTTP " + exception.getStatusCode().value()
                            + ": " + exception.getResponseBodyAsString(),
                    exception);
        }
    }

    private void validateConfiguration() {
        if (apiKey.isBlank() || projectId.isBlank() || branchId.isBlank()) {
            throw new NeonRestoreException(
                    "Faltan NEON_API_KEY, NEON_PROJECT_ID o NEON_BRANCH_ID en la configuracion.");
        }
    }

    public static class NeonRestoreException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NeonRestoreException(String message) {
            super(message);
        }

        public NeonRestoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
