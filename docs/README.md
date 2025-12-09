# Real-Time Geo-Fencing API - Documentation

Welcome to the Real-Time Geo-Fencing API documentation!

## 📚 Documentation Pages

- **[Home](index.html)** - Overview, features, and quick start guide
- **[API Reference](api-docs.html)** - Complete REST API documentation with examples
- **[WebSocket Demo](websocket-demo.html)** - Interactive WebSocket testing interface

## 🚀 Quick Links

- **Swagger UI**: http://localhost:8080/swagger-ui.html (local only)
- **GitHub Repository**: https://github.com/meliharik/realtime_geo_fencing_service
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs (local only)

## 🔧 Local Development

To test the API locally:

```bash
# 1. Start services
docker-compose up -d postgres redis

# 2. Run application
mvn spring-boot:run

# 3. Access Swagger UI
open http://localhost:8080/swagger-ui.html

# 4. Test WebSocket
open http://localhost:8080/websocket-test.html
```

## 📖 About

This documentation is hosted on GitHub Pages and provides:

- Interactive API documentation
- Code examples in multiple languages (JavaScript, Python, Java, cURL)
- WebSocket testing interface
- Architecture diagrams and performance metrics

## 🤝 Contributing

Found an issue with the documentation? Please [open an issue](https://github.com/meliharik/realtime-geo-fencing-service/issues) or submit a pull request!

---

Built with ❤️ using Spring Boot, PostGIS, Redis, and WebSockets
