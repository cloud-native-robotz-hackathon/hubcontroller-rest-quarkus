# Robot Hub Controller - Quarkus REST API

![test and build](https://github.com/cloud-native-robotz-hackathon/hubcontroller-rest-quarkus/actions/workflows/ci.yml/badge.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.14.1-blue)](https://quarkus.io)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/projects/jdk/17/)

A cloud-native Robot Hub Controller built with Quarkus that provides a centralized REST API gateway for controlling multiple robots in hackathon and robotics competition environments.

## 🤖 Overview

The Robot Hub Controller acts as a central command and control system that:
- **Routes Commands**: Forwards REST API calls to appropriate robot endpoints based on authentication tokens
- **Manages Connections**: Tracks robot status, connectivity, and operational state
- **Provides Dashboard**: Real-time web-based monitoring interface with WebSocket connectivity
- **Handles Discovery**: Automatic robot discovery and registration
- **Supports Scale**: Designed for multi-robot environments in cloud-native infrastructures

## ✨ Features

### Core Functionality
- 🎮 **Robot Control**: Forward, backward, left/right turn commands with precise distance/angle control
- 📊 **Real-time Monitoring**: WebSocket-powered dashboard for live robot status tracking
- 🔌 **Dynamic Registration**: Automatic robot discovery and token-based routing
- 📷 **Sensor Integration**: Distance sensing and camera feed access
- 🚦 **Health Checks**: Robot connectivity and operational status monitoring

### Technical Capabilities
- 🏗️ **Cloud-Native**: Built for Kubernetes/OpenShift with container-first design
- 🔄 **Real-time Updates**: WebSocket communication for instant status updates
- 📝 **OpenAPI Integration**: Auto-generated API documentation with Swagger UI
- 🧪 **Testing Support**: Comprehensive test setup with MockServer integration
- 🐳 **Multi-deployment**: Support for JVM, native, and container deployments

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web Dashboard │    │  REST Clients    │    │  Mobile Apps    │
│   (WebSocket)   │    │                  │    │                 │
└─────────┬───────┘    └─────────┬────────┘    └─────────┬───────┘
          │                      │                       │
          └──────────────────────┼───────────────────────┘
                                 │
                    ┌────────────▼─────────────┐
                    │   Hub Controller API     │
                    │     (Quarkus REST)       │
                    └────────────┬─────────────┘
                                 │
                    ┌────────────▼─────────────┐
                    │   Robot Status Manager   │
                    │   + Token Mapper         │
                    └────────────┬─────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────▼───────┐
│   Robot A       │    │   Robot B       │    │   Robot N       │
│   (IP:5000)     │    │   (IP:5000)     │    │   (IP:5000)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- **Java 17+** (OpenJDK recommended)
- **Maven 3.8.1+**
- **Docker or Podman** (for containerized testing)

### Local Development

```bash
# Clone the repository
git clone https://github.com/cloud-native-robotz-hackathon/hubcontroller-rest-quarkus.git
cd hubcontroller-rest-quarkus

# Start development mode with live reload
./mvnw quarkus:dev
```

The application will start on `http://localhost:8080` with:
- 🌐 **Dashboard**: `http://localhost:8080/dashboard.html`
- 📚 **API Documentation**: `http://localhost:8080/q/swagger-ui/`
- ❤️ **Health Check**: `http://localhost:8080/q/health`

## 🛠️ Setup Instructions

### 1. Container Runtime Setup (Linux)

For local testing with Testcontainers, configure Podman:

```bash
# Install Podman (Fedora/RHEL/CentOS)
sudo dnf install podman podman-docker

# Enable Podman socket with Docker API compatibility
systemctl --user enable podman.socket --now

# Configure environment variables
export DOCKER_HOST=unix:///run/user/${UID}/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
```

### 2. Robot Configuration

Configure robot mappings in `src/main/resources/application.properties`:

```properties
# Development robot mapping
%dev.robot.map={"robot1": "192.168.1.100", "robot2": "192.168.1.101"}

# Production uses environment variable or ConfigMap
robot.map={}
```

### 3. Run Tests

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify
```

## 📡 API Reference

### Robot Control Endpoints

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| `GET` | `/robot/status` | Check hub controller status | `user_key` (optional) |
| `GET` | `/robot/remote_status` | Check specific robot status | `user_key` (required) |
| `GET` | `/robot/distance` | Get distance sensor reading | `user_key` (required) |
| `GET` | `/robot/camera` | Get camera feed/image | `user_key` (required) |
| `POST` | `/robot/forward/{length_in_cm}` | Move robot forward | `user_key`, `length_in_cm` |
| `POST` | `/robot/backward/{length_in_cm}` | Move robot backward | `user_key`, `length_in_cm` |
| `POST` | `/robot/left/{degrees}` | Turn robot left | `user_key`, `degrees` |
| `POST` | `/robot/right/{degrees}` | Turn robot right | `user_key`, `degrees` |

### Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/robot/disconnect/{robotId}` | Disconnect robot |
| `POST` | `/robot/runapp/{robotId}` | Start robot application |
| `WS` | `/dashboard/{clientId}` | WebSocket for real-time updates |

### Example API Usage

```bash
# Check robot status
curl "http://localhost:8080/robot/remote_status?user_key=robot1"

# Move robot forward 50cm
curl -X POST -d "user_key=robot1" \
  "http://localhost:8080/robot/forward/50"

# Turn robot right 90 degrees
curl -X POST -d "user_key=robot1" \
  "http://localhost:8080/robot/right/90"

# Get distance reading
curl "http://localhost:8080/robot/distance?user_key=robot1"
```

## 🚢 Deployment

### Local Development

```bash
# Start with live reload
./mvnw quarkus:dev

# Access dashboard
open http://localhost:8080/dashboard.html
```

### OpenShift/Kubernetes

```bash
# Deploy to current OpenShift project
./mvnw clean package -Dquarkus.openshift.deploy=true

# Or build and deploy separately
./mvnw clean package
oc apply -f target/kubernetes/
```

### Container Deployment

```bash
# Build JVM container
./mvnw clean package
docker build -f src/main/docker/Dockerfile.jvm -t hub-controller:latest .

# Build native container (faster startup, smaller memory footprint)
./mvnw clean package -Dnative
docker build -f src/main/docker/Dockerfile.native -t hub-controller:native .

# Run container
docker run -i --rm -p 8080:8080 hub-controller:latest
```

### Configuration

Create a `robot-mapping-configmap` for production environments:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: robot-mapping-configmap
data:
  MAP: |
    {
      "robot1": "192.168.1.100",
      "robot2": "192.168.1.101",
      "robot3": "robot3.robot.svc.cluster.local"
    }
```

## 🧪 Testing

### Local Testing

```bash
# Test hub controller status
curl "http://localhost:8080/robot/remote_status?user_key=data"

# Test with MockServer (development mode)
curl "http://localhost:8080/robot/distance?user_key=test"
```

### CRC (CodeReady Containers) Testing

```bash
# Get the route URL
oc get route hub-controller-quarkus

# Test the deployed application
curl -k "http://hub-controller-quarkus-hubcontroller-dev.apps-crc.testing/robot/remote_status?user_key=data"
```

### Integration Tests

The project includes MockServer-based integration tests that simulate robot responses:

```bash
# Run all tests including integration tests
./mvnw clean verify

# Run only integration tests
./mvnw clean test-compile failsafe:integration-test
```

## 📊 Monitoring & Observability

### Health Checks

- **Liveness**: `GET /q/health/live`
- **Readiness**: `GET /q/health/ready`
- **Metrics**: `GET /q/metrics` (if enabled)

### Dashboard Features

The web dashboard provides:
- 📊 Real-time robot status updates
- 🔄 Connection state monitoring
- 📈 Operation count tracking
- 🎮 Manual robot control interface
- 🔌 Connect/disconnect controls

## 🔧 Configuration Options

### Application Properties

```properties
# Server configuration
quarkus.http.port=8080
quarkus.http.test-port=0

# OpenShift integration
quarkus.openshift.route.expose=true
quarkus.openshift.env.mapping.MAP.from-configmap=robot-mapping-configmap

# Container image settings
quarkus.container-image.builder=jib
quarkus.container-image.insecure=true

# Development settings
%dev.robot.map={"data": "data.lan"}
%dev.quarkus.mockserver.devservices.reuse=false
```

### Environment Variables

- `MAP`: JSON string mapping robot tokens to addresses
- `DOCKER_HOST`: Docker daemon socket (for testing)
- `TESTCONTAINERS_RYUK_DISABLED`: Disable Ryuk for Testcontainers

## 🐛 Troubleshooting

### Common Issues

**Robot Connection Failures**
```bash
# Check robot endpoint connectivity
curl http://robot-ip:5000/status

# Verify token mapping
curl "http://localhost:8080/robot/status?user_key=your-token"
```

**WebSocket Connection Issues**
- Ensure browser supports WebSockets
- Check firewall settings for port 8080
- Verify the dashboard connects properly in browser dev tools

**Container Build Failures**
```bash
# For native builds, ensure GraalVM is available
./mvnw clean package -Dnative -Dquarkus.native.container-build=true
```

### Development Tips

1. **Live Reload**: Use `./mvnw quarkus:dev` for automatic code reloading
2. **Debug Mode**: Add `-Ddebug=5005` to enable remote debugging
3. **Logging**: Set `quarkus.log.level=DEBUG` for verbose logging
4. **Testing**: Use `@QuarkusTest` annotations for integration testing


## 📋 Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Framework** | Quarkus | 3.14.1 |
| **Language** | Java | 17+ |
| **Build Tool** | Maven | 3.8.1+ |
| **REST API** | JAX-RS (RESTEasy Reactive) | - |
| **WebSockets** | Jakarta WebSocket | - |
| **JSON Processing** | Jackson | - |
| **API Documentation** | OpenAPI/Swagger | - |
| **Health Checks** | MicroProfile Health | - |
| **Container Runtime** | Docker/Podman | - |
| **Orchestration** | Kubernetes/OpenShift | - |
| **Testing** | JUnit 5, REST Assured, MockServer | - |

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

