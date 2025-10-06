# EDC Identity Hub

## Introduction 
The EDC Identity Hub is a component of the Eclipse Dataspace Connector (EDC) ecosystem that provides decentralized identity and credential management services. It enables secure participant identification, authentication, and credential verification within dataspace environments.

Key features:
- Decentralized Identifier (DID) resolution and management
- Verifiable credential issuance and verification
- Identity authentication APIs
- Integration with the EDC connector framework

## Getting Started

### Prerequisites
- Java Development Kit (JDK) 17 or later
- Gradle 8.12 or later
- Docker (for containerized deployment)

### Installation

#### Option 1: Running Locally

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd edc-identityhub
   ```

2. Build the application:
   ```bash
   ./gradlew clean build
   ```

3. Run the application:
   ```bash
   ./gradlew run
   ```

#### Option 2: Using Docker

1. Build the Docker image:
   ```bash
   docker build -t localhost:5432/edc-identityhub .
   ```

2. Run the container:
   ```bash
   docker run -p 8181:8181 -p 8182:8182 -p 8183:8183 -p 8184:8184 localhost:5432/edc-identityhub
   ```

### Configuration

Configuration is provided through `config.properties`, with key settings:

```properties
# API Ports and Paths
web.http.port=8181
web.http.path=/api
web.http.identity.port=8182
web.http.identity.path=/api/identity
web.http.credentials.port=8183
web.http.credentials.path=/api/credentials
web.http.did.port=8184
web.http.did.path=/

# Identity Hub Configuration
edc.ih.iam.id=default
edc.ih.api.superuser.key=change-me
```

For production deployments, ensure you modify security settings appropriately.

## API Reference

The Identity Hub exposes several REST APIs:

- **Identity API**: `/api/identity` on port 8182
  - Participant identity management and authentication

- **Credentials API**: `/api/credentials` on port 8183
  - Verifiable credential issuance and verification

- **DID API**: `/` on port 8184
  - DID resolution and document management

## Build and Test

### Building the Project
```bash
./gradlew clean build
```

### Running Tests
```bash
./gradlew test
```

### Building a Distribution
```bash
./gradlew shadowJar
```
This creates a runnable JAR in `build/libs/identity-hub.jar`


## Docker Deployment

The provided Dockerfile creates a multi-stage build for optimized container size:

1. Builder stage compiles the application
2. Runtime stage runs with the minimal required dependencies

## Kubernetes Deployment

For Kubernetes deployment, use the Helm chart available in the `helm-edc-connector` repository.

## Contributing

We welcome contributions to the EDC Identity Hub. Please feel free to submit pull requests, create issues, or suggest improvements.

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

For more information on contributing to EDC projects, see [Eclipse Dataspace Connector Contributing Guide](https://github.com/eclipse-edc/Connector/blob/main/CONTRIBUTING.md).