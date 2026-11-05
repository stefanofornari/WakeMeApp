# Development Framework

## Purpose

How `WakeMeUp` is built, tested, and installed.

## Technology Stack

- **Language / Runtime**: Java 25
- **Frameworks / Libraries**: AtlantaFX, Ikonli Swing and Material Design 2 icon packs (`12.3.1`)
- **Build Tool**: Maven
- **Test Tools**: 
  - JUnit 5 (`5.10.2`), 
  - AssertJ (`3.27.7`), 
  - com.github.stefanofornari:xtest (https://github.com/stefanofornari/xtest)
  - Maven Surefire 3.2.5
- **HTTP Client**: JDK Http Client
- **Logging**: `java.util.logging`, no logging framework or facade (no SLF4J, Logback, Log4j)
- **Configuration**: what the JDK provides (`java.util.Properties`, `java.util.prefs`) or Apache Commons Configuration; no other configuration library

## Development Workflow

- Follow TDD (see coding_standard.md)
- Verify changes with `mvn clean package` before handing them over.

## Network testing harness
- In the need to access external services via HTTP APIs requests, create testing
  doubles, possibly that do not require network access
  - if the client used is a java.net.http.HttpClient, use xtes's ste.xtest.net.http
    package to create stubs
  - if the other cases use WireMock (https://github.com/wiremock/wiremock)

## Build and Execution

- **Build**: `mvn clean package`
- **Test**: `mvn test`
- **Run**: run as standalone javafx application

### Keywords
- development-framework
- toolchain
- build
- testing
- workflow
