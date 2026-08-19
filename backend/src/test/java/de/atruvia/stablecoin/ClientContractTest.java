package de.atruvia.stablecoin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.client.dto.CircleWalletBalanceDto;
import de.atruvia.stablecoin.client.dto.TaurusTransactionRequestDto;
import de.atruvia.stablecoin.client.dto.TaurusTransactionResponseDto;
import de.atruvia.stablecoin.client.http.HttpChainalysisClient;
import de.atruvia.stablecoin.client.http.HttpCircleWalletClient;
import de.atruvia.stablecoin.client.http.HttpTaurusCustodyClient;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WireMock-Contract-Tests für externe HTTP-Clients.
 *
 * Verifiziert, dass HttpCircleWalletClient, HttpTaurusCustodyClient und
 * HttpChainalysisClient korrekt mit den API-Endpunkten der Drittsysteme
 * kommunizieren und Fehlerszenarien robust behandeln.
 */
class ClientContractTest {

    private static WireMockServer wireMock;
    private static String baseUrl;

    // Clients — direkt instanziiert (kein Spring-Kontext benötigt)
    private static HttpCircleWalletClient circleClient;
    private static HttpTaurusCustodyClient taurusClient;
    private static HttpChainalysisClient chainalysisClient;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();

        RestClient.Builder builder = RestClient.builder();

        circleClient = new HttpCircleWalletClient(builder, baseUrl, "test-api-key", "WALLET-001");
        taurusClient = new HttpTaurusCustodyClient(RestClient.builder(), baseUrl, "test-taurus-key");
        chainalysisClient = new HttpChainalysisClient(RestClient.builder(), baseUrl, "test-chain-key");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Circle Wallet Client — Transfer
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CC-01: Circle initiateTransfer — 200 OK → CircleTransferResponseDto")
    void circle_initiateTransfer_success() {
        wireMock.stubFor(post(urlEqualTo("/v1/w3s/developer/transactions/transfer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "tx-circle-001",
                                  "status": "COMPLETE",
                                  "transactionHash": "0xabc123def456",
                                  "createDate": "2026-08-19T10:00:00"
                                }
                                """)));

        CircleTransferResponseDto response = circleClient.initiateTransfer(
                new CircleTransferRequestDto(
                        "idem-key-001",
                        new CircleTransferRequestDto.Source("wallet", "MASTER-WALLET"),
                        new CircleTransferRequestDto.Destination("blockchain", "0xDest", "MATIC"),
                        new CircleTransferRequestDto.Amount("1000.00", "USDC")
                )
        );

        assertThat(response.id()).isEqualTo("tx-circle-001");
        assertThat(response.status()).isEqualTo("COMPLETE");
        assertThat(response.transactionHash()).isEqualTo("0xabc123def456");
    }

    @Test
    @DisplayName("CC-02: Circle initiateTransfer — 500 Server Error → RuntimeException")
    void circle_initiateTransfer_serverError_throwsRuntimeException() {
        wireMock.stubFor(post(urlEqualTo("/v1/w3s/developer/transactions/transfer"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        assertThatThrownBy(() -> circleClient.initiateTransfer(
                new CircleTransferRequestDto(
                        "idem-key-002",
                        new CircleTransferRequestDto.Source("wallet", "MASTER-WALLET"),
                        new CircleTransferRequestDto.Destination("blockchain", "0xDest", "MATIC"),
                        new CircleTransferRequestDto.Amount("500.00", "USDC")
                )
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Circle API error [500")
                .hasMessageContaining("Internal Server Error");
    }

    @Test
    @DisplayName("CC-03: Circle initiateTransfer — 400 Bad Request → RuntimeException mit Körper")
    void circle_initiateTransfer_badRequest_throwsRuntimeException() {
        wireMock.stubFor(post(urlEqualTo("/v1/w3s/developer/transactions/transfer"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Invalid wallet address\"}")));

        assertThatThrownBy(() -> circleClient.initiateTransfer(
                new CircleTransferRequestDto(
                        "idem-key-003",
                        new CircleTransferRequestDto.Source("wallet", "MASTER-WALLET"),
                        new CircleTransferRequestDto.Destination("blockchain", "INVALID", "MATIC"),
                        new CircleTransferRequestDto.Amount("100.00", "USDC")
                )
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Circle API error [400")
                .hasMessageContaining("Invalid wallet address");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Circle Wallet Client — Wallet Balance
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CC-04: Circle getWalletBalance — 200 OK → korrekte Balances")
    void circle_getWalletBalance_success() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/w3s/wallets/0xWallet001/balances"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "balances": [
                                    {"currency": "USDC", "amount": "250.500000"},
                                    {"currency": "EURC", "amount": "100.000000"}
                                  ]
                                }
                                """)));

        CircleWalletBalanceDto response = circleClient.getWalletBalance("0xWallet001");

        assertThat(response.balances()).hasSize(2);
        assertThat(response.balances().get(0).currency()).isEqualTo("USDC");
        assertThat(response.balances().get(0).amount()).isEqualTo("250.500000");
    }

    @Test
    @DisplayName("CC-05: Circle getWalletBalance — 404 Not Found → RuntimeException")
    void circle_getWalletBalance_notFound_throwsRuntimeException() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/w3s/wallets/0xMissing/balances"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"error\":\"Wallet not found\"}")));

        assertThatThrownBy(() -> circleClient.getWalletBalance("0xMissing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Circle API error [404");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Taurus Custody Client
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CC-06: Taurus signAndSubmit — 200 OK → TaurusTransactionResponseDto")
    void taurus_signAndSubmit_success() {
        wireMock.stubFor(post(urlEqualTo("/v2/transactions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "taurus-tx-001",
                                  "status": "SUBMITTED",
                                  "signature": "0xsig123abc",
                                  "submittedAt": "2026-08-19T10:00:00"
                                }
                                """)));

        TaurusTransactionResponseDto response = taurusClient.signAndSubmit(
                new TaurusTransactionRequestDto(
                        "USDC", "0xSourceWallet", "0xDestWallet", "1000.00",
                        new TaurusTransactionRequestDto.Metadata("idem-001", "cust-001")
                )
        );

        assertThat(response.id()).isEqualTo("taurus-tx-001");
        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.signature()).isEqualTo("0xsig123abc");
    }

    @Test
    @DisplayName("CC-07: Taurus signAndSubmit — 403 Limit Exceeded → TaurusLimitExceededException")
    void taurus_signAndSubmit_403_throwsTaurusLimitExceededException() {
        wireMock.stubFor(post(urlEqualTo("/v2/transactions"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withBody("{\"error\":\"Daily limit exceeded\",\"limit\":\"1000000\"}")));

        assertThatThrownBy(() -> taurusClient.signAndSubmit(
                new TaurusTransactionRequestDto(
                        "USDC", "0xSrc", "0xDest", "2000000.00",
                        new TaurusTransactionRequestDto.Metadata("idem-002", "cust-002")
                )
        ))
                .isInstanceOf(TaurusLimitExceededException.class)
                .hasMessageContaining("403 Forbidden");
    }

    @Test
    @DisplayName("CC-08: Taurus signAndSubmit — 500 Server Error → RuntimeException")
    void taurus_signAndSubmit_serverError_throwsRuntimeException() {
        wireMock.stubFor(post(urlEqualTo("/v2/transactions"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\":\"Internal server error\"}")));

        assertThatThrownBy(() -> taurusClient.signAndSubmit(
                new TaurusTransactionRequestDto(
                        "USDC", "0xSrc", "0xDest", "100.00",
                        new TaurusTransactionRequestDto.Metadata("idem-003", "cust-003")
                )
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Taurus API error [500");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Chainalysis Client
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CC-09: Chainalysis screenAddress — LOW RISK → approved=true")
    void chainalysis_screenAddress_lowRisk_approvedTrue() {
        wireMock.stubFor(post(urlEqualTo("/v1/addresses/screen"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "address": "0xSafeWallet",
                                  "riskScore": "LOW",
                                  "riskCategories": [],
                                  "sanctionedEntity": false,
                                  "approved": true
                                }
                                """)));

        AddressScreenResponseDto response = chainalysisClient.screenAddress(
                new AddressScreenRequestDto("0xSafeWallet", "USDC", "MATIC", "outgoing")
        );

        assertThat(response.approved()).isTrue();
        assertThat(response.riskScore()).isEqualTo("LOW");
        assertThat(response.sanctionedEntity()).isFalse();
    }

    @Test
    @DisplayName("CC-10: Chainalysis screenAddress — CRITICAL RISK → approved=false, sanctionedEntity=true")
    void chainalysis_screenAddress_criticalRisk_approvedFalse() {
        wireMock.stubFor(post(urlEqualTo("/v1/addresses/screen"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "address": "0xHighRiskAddress000000000000000000000000",
                                  "riskScore": "CRITICAL",
                                  "riskCategories": ["SANCTIONS", "DARKNET_MARKET"],
                                  "sanctionedEntity": true,
                                  "approved": false
                                }
                                """)));

        AddressScreenResponseDto response = chainalysisClient.screenAddress(
                new AddressScreenRequestDto("0xHighRiskAddress000000000000000000000000", "USDC", "MATIC", "outgoing")
        );

        assertThat(response.approved()).isFalse();
        assertThat(response.riskScore()).isEqualTo("CRITICAL");
        assertThat(response.sanctionedEntity()).isTrue();
        assertThat(response.riskCategories()).contains("SANCTIONS", "DARKNET_MARKET");
    }

    @Test
    @DisplayName("CC-11: Chainalysis screenAddress — 503 Service Unavailable → RuntimeException (fail-closed)")
    void chainalysis_screenAddress_serviceUnavailable_throwsRuntimeException() {
        wireMock.stubFor(post(urlEqualTo("/v1/addresses/screen"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withBody("{\"error\":\"Service temporarily unavailable\"}")));

        assertThatThrownBy(() -> chainalysisClient.screenAddress(
                new AddressScreenRequestDto("0xAnyWallet", "USDC", "MATIC", "outgoing")
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Chainalysis API error [503");
    }
}
