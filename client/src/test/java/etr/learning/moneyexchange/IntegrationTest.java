package etr.learning.moneyexchange;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import etr.learning.moneyexchange.MoneyExchangeApp.ExchangeCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@WireMockTest(httpPort = IntegrationTest.WIREMOCK_PORT)
class IntegrationTest {
	static final int WIREMOCK_PORT = 1234;
	private final ExchangeCalculator exchange = new ExchangeCalculator("http://localhost:" + WIREMOCK_PORT);

	@Container
	static DockerComposeContainer<?> env = new DockerComposeContainer<>(new File("src/test/resources/compose-test.yml"))
			.withExposedService("conversion-rates-api", 8080, Wait.forHttp("/currencies").forStatusCode(200))
			.withOptions("--compatibility")
			.withLocalCompose(true);

	@Test
	void test() {
		//given
		String testcontainerUrl = "http://%s:%s".formatted(
				env.getServiceHost("conversion-rates-api", 8080),
				env.getServicePort("conversion-rates-api", 8080)
		);

		// and
		stubFor(get(urlMatching("/currencies/.*"))
				.willReturn(aResponse()
						.proxiedFrom(testcontainerUrl)));

		stubFor(get(urlMatching("/currencies/GBP"))
				.willReturn(aResponse()
						.withBody("Wrong response, definitely not a number!")));

		stubFor(get(urlMatching("/currencies/RON.*"))
				.willReturn(aResponse()
						.withFixedDelay(3_000)
						.proxiedFrom(testcontainerUrl)));

		// then
		var okResponse = exchange.toEuro(100.00, "USD");
		assertThat(okResponse)
				.extracting(ResponseEntity::getStatusCode, ResponseEntity::getBody)
				.containsExactly(HttpStatus.OK, "Exchanging 100.0 USD at a rate of 0.92 will give you 92.0 EUR");

		// and
		var nokResponse = exchange.toEuro(100.00, "GBP");
		assertThat(nokResponse)
				.extracting(ResponseEntity::getStatusCode, ResponseEntity::getBody)
				.containsExactly(HttpStatus.INTERNAL_SERVER_ERROR, "Ooops! There was an error oun our side!");

		// and
		var slowResponse = exchange.toEuro(100.00, "RON");
		assertThat(slowResponse.getStatusCode())
				.isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
	}

}
