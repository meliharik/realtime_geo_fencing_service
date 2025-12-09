package com.geofencing.engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofencing.engine.dto.GpsEventRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GeoFencingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnHealthy() throws Exception {
        mockMvc.perform(get("/api/geofencing/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldCheckViolationWithValidGpsEvent() throws Exception {
        GpsEventRecord gpsEvent = new GpsEventRecord(
                "SC-TEST-001",
                37.7800,
                -122.4150,
                Instant.now(),
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/geofencing/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gpsEvent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scooterId").value("SC-TEST-001"));
    }

    @Test
    void shouldGetAllActiveZones() throws Exception {
        mockMvc.perform(get("/api/geofencing/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldGetCacheStatistics() throws Exception {
        mockMvc.perform(get("/api/geofencing/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHealthy").exists());
    }

    @Test
    void shouldCheckViolationQuickly() throws Exception {
        mockMvc.perform(get("/api/geofencing/check-quick")
                        .param("scooterId", "SC-001")
                        .param("lat", "37.7700")
                        .param("lon", "-122.4000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
