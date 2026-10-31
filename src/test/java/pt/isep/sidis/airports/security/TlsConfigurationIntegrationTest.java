package pt.isep.sidis.airports.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tls")
@TestPropertySource(properties = {
        "SSL_KEYSTORE=file:certs/airports.p12",
        "SSL_TRUSTSTORE=file:certs/truststore.p12",
        "SSL_PASSWORD=changeit",
        "airports.db.name=tls_test_db"
})
class TlsConfigurationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired(required = false)
    private SslBundles sslBundles;

    @Test
    void sslBundlesLoadedWithTls13Protocols() {
        assertThat(sslBundles).isNotNull();

        SslBundle serverBundle = sslBundles.getBundle("server");
        assertThat(serverBundle).isNotNull();
        assertThat(serverBundle.getOptions().getEnabledProtocols()).contains("TLSv1.3");

        SslBundle clientBundle = sslBundles.getBundle("client");
        assertThat(clientBundle).isNotNull();
        assertThat(clientBundle.getOptions().getEnabledProtocols()).contains("TLSv1.3");
    }

    @Test
    void httpsEndpointAnswersWhenClientTrustsDevCa() throws Exception {
        assertThat(sslBundles).isNotNull();
        SslBundle clientBundle = sslBundles.getBundle("client");

        // Construct JDK HttpClient with the AISafe Dev CA trusted via client bundle
        HttpClient client = HttpClient.newBuilder()
                .sslContext(clientBundle.createSslContext())
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    void plainHttpClientFailsHandshakeOnHttpsPort() {
        // A client without SSLContext connecting with standard HTTPS or wrong trust
        HttpClient defaultClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        // Should throw SSLHandshakeException because standard JVM truststore does not have AISafe Dev CA
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            defaultClient.send(request, HttpResponse.BodyHandlers.ofString());
        });
    }
}
