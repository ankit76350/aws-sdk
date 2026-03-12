# AWS Lambda Spring Boot 3 - Local Development Setup

## Project Overview

A serverless Spring Boot 3 REST API designed to run on AWS Lambda, using the `aws-serverless-java-container` library to bridge Spring Boot and the Lambda runtime. Tested locally using AWS SAM CLI.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 3.4.5 |
| Java | 17 |
| aws-serverless-java-container | 2.1.5 |
| AWS SAM CLI | latest |
| Maven | 3.x |

---

## Project Structure

```
aws-sdk/
├── src/
│   └── main/java/org/example/
│       ├── Application.java              # Spring Boot entry point (@SpringBootApplication)
│       ├── StreamLambdaHandler.java      # Lambda handler (bridges Lambda ↔ Spring Boot)
│       └── controller/
│           └── PingController.java       # GET /ping endpoint
├── template.yaml                         # SAM configuration for local testing
└── pom.xml                               # Maven build config
```

---

## Key Files Explained

### `StreamLambdaHandler.java`
The Lambda entry point. Wraps the Spring Boot application inside a `SpringBootLambdaContainerHandler` which translates incoming API Gateway v1 events into standard HTTP requests that Spring can process.

```java
public class StreamLambdaHandler implements RequestStreamHandler {
    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> handler;
    static {
        handler = SpringBootLambdaContainerHandler.getAwsProxyHandler(Application.class);
    }

    @Override
    public void handleRequest(InputStream in, OutputStream out, Context ctx) throws IOException {
        handler.proxyStream(in, out, ctx);
    }
}
```

### `PingController.java`
A simple REST controller with one endpoint:

```
GET /ping → {"pong": "Hello, World!"}
```

### `pom.xml` — Key design decisions

**Tomcat is excluded intentionally:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```
There is no embedded web server — Lambda handles HTTP via API Gateway, not Tomcat. This is why `mvn spring-boot:run` starts and immediately exits.

**Two Maven build profiles:**

| Profile | Purpose |
|---------|---------|
| `shaded-jar` | Builds a fat JAR for SAM local testing and AWS deployment |
| `assembly-zip` *(default)* | Builds a ZIP for AWS Lambda deployment (skips JAR install) |

### `template.yaml` — SAM configuration

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Transform: AWS::Serverless-2016-10-31

Globals:
  Function:
    Timeout: 30
    MemorySize: 512

Resources:
  SpringBootFunction:
    Type: AWS::Serverless::Function
    Properties:
      Handler: org.example.StreamLambdaHandler::handleRequest
      Runtime: java17
      CodeUri: target/aws-sdk-1.0-SNAPSHOT.jar
      Events:
        ApiEvents:
          Type: Api              # API Gateway v1 (REST API) — must match AwsProxyRequest
          Properties:
            Path: /{proxy+}     # Catch-all proxy path
            Method: ANY
```

> **Critical:** `Type: Api` (v1) must be used — NOT `Type: HttpApi` (v2). The `aws-serverless-java-container` library parses API Gateway v1 format (`AwsProxyRequest`). Using `HttpApi` causes `InvalidRequestEventException`.

---

## Prerequisites

```bash
# Install SAM CLI
brew install aws-sam-cli

# Install Docker Desktop (required by SAM to emulate Lambda runtime)
brew install --cask docker
```

Verify:
```bash
sam --version
docker --version
```

---

## Running Locally — Step by Step

### Step 1: Build the fat JAR
```bash
mvn package -P shaded-jar
```
Output: `target/aws-sdk-1.0-SNAPSHOT.jar`

### Step 2: Start Docker Desktop
Open Docker Desktop from Applications. Wait for the whale icon in the menu bar to show "Docker Desktop is running".

### Step 3: Start the local API
```bash
sam local start-api
```

SAM will:
1. Pull the `public.ecr.aws/lambda/java:17-rapid-x86_64` Docker image (first time only — slow)
2. Start a local HTTP server at `http://127.0.0.1:3000`
3. Emulate API Gateway + Lambda on each request

### Step 4: Test
```bash
curl http://127.0.0.1:3000/ping
```

Expected response:
```json
{"pong": "Hello, World!"}
```

---

## Common Errors & Fixes

### `mvn spring-boot:run` exits immediately
**Cause:** Tomcat is excluded — no web server to keep the app running.
**Fix:** Use `sam local start-api` instead. This is a Lambda project, not a standalone server.

---

### `zsh: command not found: sam`
**Cause:** SAM CLI not installed.
**Fix:**
```bash
brew install aws-sam-cli
```

---

### `Do you have Docker or Finch installed and running?`
**Cause:** Docker Desktop is not running.
**Fix:** Open Docker Desktop from Applications and wait for it to fully start.

---

### `InvalidRequestEventException: The incoming event is not a valid request from Amazon API Gateway`
**Cause:** `template.yaml` was using `Type: HttpApi` (API Gateway v2), but the handler expects API Gateway v1 format.
**Fix:** Change event type in `template.yaml`:
```yaml
# Wrong
Type: HttpApi

# Correct
Type: Api
```

---

## After Code Changes

Every time you modify Java code, rebuild before testing:
```bash
mvn package -P shaded-jar && sam local start-api
```

---

## Deploying to AWS (when ready)

```bash
# Build the deployment ZIP
mvn package

# Deploy using SAM
sam deploy --guided
```
