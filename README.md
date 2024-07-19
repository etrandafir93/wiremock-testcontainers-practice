# Wiremock - Testcontainers Demo

This project aims to show how to use `Testcontainers` for Java to spin up a Docker container with a service providing a REST API for testing purposes. It also demonstrates using `WireMock` to proxy this service and inject delays or faults for specific endpoints.


### JUnit5 Extensions
```java
@Testcontainers
@WireMockTest(httpPort = IntegrationTest.WIREMOCK_PORT)
class IntegrationTest {
    // ...
}
```
The `@Testcontainers` and `@WireMockTest` class-level annotations are JUnit5 extensions. They will help us manage the lifecycle of the Testcontainer and the WireMock server.

### Docker Compose Container
```java
@Container
static DockerComposeContainer<?> env = new DockerComposeContainer<>(new File("src/test/resources/compose-test.yml"))
  .withExposedService("conversion-rates-api", 8080, Wait.forHttp("/currencies").forStatusCode(200)) 
  .withLocalCompose(true);
```
`DockerComposeContainer<?> env` Sets up a Docker Compose environment using the _compose-test.yml_ file.

`.withExposedService("conversion-rates-api", 8080, Wait.forHttp("/currencies").forStatusCode(200))` Exposes our REST API service on port 8080 and waits until the _/currencies_ endpoint returns a 200 status code.

### WireMock Stubs
```java
String testcontainerUrl = "http://%s:%s".formatted(
  env.getServiceHost("conversion-rates-api", 8080),
  env.getServicePort("conversion-rates-api", 8080)
);

stubFor(get(urlMatching("/currencies/.*"))
  .willReturn(aResponse().proxiedFrom(testcontainerUrl)));
```
`testcontainerUrl` Constructs the URL for the _conversion-rates-api_ service, running in the Docker container launched by Testcontainers.

`stubFor(...).willReturn(aResponse().proxiedFrom(...)` Sets up a stub for any GET request for the path _/currencies_ and proxies the response from _testcontainerUrl_.



### Injecting Failures and Delays
```java
stubFor(get(urlMatching("/currencies/GBP"))
  .willReturn(aResponse()
    .withBody("Wrong response, definitely not a number!")));

stubFor(get(urlMatching("/currencies/RON"))
  .willReturn(aResponse()
    .withFixedDelay(3_000)
      .proxiedFrom(testcontainerUrl)));
```

### Testing
```java
var okResponse = exchange.toEuro(100.00, "USD");
assertThat(okResponse)
  .extracting(ResponseEntity::getStatusCode, ResponseEntity::getBody)
  .containsExactly(HttpStatus.OK, "Exchanging 100.0 USD at a rate of 0.92 will give you 92.0 EUR");

// and
var nokResponse = exchange.toEuro(100.00, "GBP");
assertThat(nokResponse)
  .extracting(ResponseEntity::getStatusCode, ResponseEntity::getBody)
  .containsExactly(HttpStatus.INTERNAL_SERVER_ERROR, "Ooops! There was an error on our side!");

// and
var slowResponse = exchange.toEuro(100.00, "RON");
assertThat(slowResponse.getStatusCode())
  .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
```
