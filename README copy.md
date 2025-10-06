# EDC Identity Hub

This repository contains the Identity Hub component of the Eclipse Dataspace Connector (EDC).

## Overview

The Identity Hub is responsible for managing digital identities, credentials, and authentication within the EDC ecosystem. It provides:

- Identity Management API
- Credentials API
- DID (Decentralized Identifier) Resolution
- STS (Security Token Service) Integration

## Getting Started

### Prerequisites

- Java 17
- Docker (for containerization)
- Gradle 8.x

### Build

```bash
./gradlew clean build
```

### Run

```bash
./gradlew bootRun
```

## Configuration

The Identity Hub can be configured using environment variables or a `config.properties` file. See the configuration section for details.