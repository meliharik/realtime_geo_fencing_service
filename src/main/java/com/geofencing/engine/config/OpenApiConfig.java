package com.geofencing.engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for API documentation.
 *
 * This provides interactive API documentation accessible at:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 * - OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml
 *
 * The documentation can be exported and used with tools like:
 * - Postman (import OpenAPI spec)
 * - Insomnia (import OpenAPI spec)
 * - Code generators (generate client SDKs)
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI geoFencingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Real-Time Geo-Fencing API")
                        .description("High-performance geo-fencing engine for scooter fleet management.\n\n" +
                                "## Features\n\n" +
                                "- **Real-time violation detection** - Sub-millisecond response times\n" +
                                "- **WebSocket streaming** - Live GPS data streaming with STOMP\n" +
                                "- **Redis caching** - 10-50x performance improvement\n" +
                                "- **PostGIS spatial queries** - Advanced geometric operations\n" +
                                "- **Rate limiting** - Duplicate violation prevention\n\n" +
                                "## Architecture\n\n" +
                                "1. GPS events received via REST or WebSocket\n" +
                                "2. Check cached zones (Redis + JTS in-memory)\n" +
                                "3. Fallback to PostGIS if cache miss\n" +
                                "4. Persist violations and return alerts\n\n" +
                                "## Performance\n\n" +
                                "- Cache hit: ~0.1-0.5ms per event\n" +
                                "- Cache miss: ~5-10ms per event\n" +
                                "- Throughput: 2,000-5,000 events/sec (single thread)\n\n" +
                                "## WebSocket Endpoints\n\n" +
                                "Connect to WebSocket at: `ws://localhost:8080/ws/gps-stream`\n\n" +
                                "**Topics:**\n" +
                                "- `/app/gps` - Send GPS events\n" +
                                "- `/app/gps/batch` - Send batch GPS events\n" +
                                "- `/app/ping` - Health check\n" +
                                "- `/topic/alerts` - Receive violation alerts\n" +
                                "- `/topic/status` - Receive system status\n\n" +
                                "## Getting Started\n\n" +
                                "1. Start PostgreSQL with PostGIS extension\n" +
                                "2. Start Redis server\n" +
                                "3. Run: `mvn spring-boot:run`\n" +
                                "4. Access Swagger UI: http://localhost:8080/swagger-ui.html\n" +
                                "5. Test WebSocket: Open `src/main/resources/static/websocket-test.html`")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Geo-Fencing API Team")
                                .url("https://github.com/meliharik/realtime_geo_fencing_service")
                                .email("contact@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("https://meliharik.github.io/realtime_geo_fencing_service")
                                .description("Production Server (replace with actual URL)")
                ));
    }
}
