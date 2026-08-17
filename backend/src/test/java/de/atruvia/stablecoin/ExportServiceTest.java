package de.atruvia.stablecoin;

import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.CustomerType;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import de.atruvia.stablecoin.service.b2b.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ExportServiceTest {

    @Mock private StablecoinTransactionRepository txRepository;
    @Mock private CustomerAccountRepository accountRepository;

    private ExportService exportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exportService = new ExportService(txRepository, accountRepository);
    }

    private CustomerAccount b2bAccount(String iban) {
        CustomerAccount acc = new CustomerAccount();
        ReflectionTestUtils.setField(acc, "id", UUID.randomUUID());
        acc.setIban(iban);
        acc.setCustomerType(CustomerType.B2B);
        return acc;
    }

    private StablecoinTransaction settledTx() {
        StablecoinTransaction tx = new StablecoinTransaction();
        ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
        tx.setAmountFiat(new BigDecimal("1000.00"));
        tx.setAmountStablecoin(new BigDecimal("1000.000000"));
        tx.setCurrency(StablecoinCurrency.USDC);
        tx.setBlockchainHash("0xhash123");
        tx.setGrossRevenue(new BigDecimal("2.50"));
        tx.setStatus(TransactionStatus.SETTLED);
        tx.setSettledAt(LocalDateTime.of(2026, 1, 15, 10, 0));
        return tx;
    }

    // ─── resolveIban ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveIban: explicit non-blank IBAN -> returns trimmed IBAN unchanged")
    void resolveIban_explicitIban_returnsIt() {
        String result = exportService.resolveIban("DE89370400440532013000");
        assertThat(result).isEqualTo("DE89370400440532013000");
    }

    @Test
    @DisplayName("resolveIban: null param -> finds first B2B account, returns its IBAN")
    void resolveIban_null_findsFirstB2bAccount() {
        CustomerAccount acc = b2bAccount("DE89370400440532013001");
        when(accountRepository.findAll()).thenReturn(List.of(acc));

        String result = exportService.resolveIban(null);

        assertThat(result).isEqualTo("DE89370400440532013001");
    }

    @Test
    @DisplayName("resolveIban: blank param + no B2B account -> NoSuchElementException")
    void resolveIban_blank_noB2bAccount_throwsNoSuchElement() {
        CustomerAccount b2cAcc = new CustomerAccount();
        b2cAcc.setCustomerType(CustomerType.B2C);
        when(accountRepository.findAll()).thenReturn(List.of(b2cAcc));

        assertThatThrownBy(() -> exportService.resolveIban("  "))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("No B2B account found");
    }

    // ─── generateCamt053 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateCamt053: valid IBAN + 1 settled tx -> XML contains IBAN and entry")
    void generateCamt053_oneTx_xmlContainsIbanAndEntry() throws Exception {
        String iban = "DE89370400440532013002";
        CustomerAccount acc = b2bAccount(iban);
        StablecoinTransaction tx = settledTx();

        when(accountRepository.findByIban(iban)).thenReturn(Optional.of(acc));
        when(txRepository.findByCustomerAccountIdAndStatus(
                eq(acc.getId()), eq(TransactionStatus.SETTLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        String xml = exportService.generateCamt053(iban);

        assertThat(xml).isNotBlank();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        XPath xpath = XPathFactory.newInstance().newXPath();
        String ibanInXml = xpath.evaluate("//*[local-name()='IBAN']", doc);
        assertThat(ibanInXml).isEqualTo(iban);
    }

    @Test
    @DisplayName("generateCamt053: valid IBAN + 0 settled tx -> valid XML with empty statement")
    void generateCamt053_noTx_validXml() throws Exception {
        String iban = "DE89370400440532013003";
        CustomerAccount acc = b2bAccount(iban);

        when(accountRepository.findByIban(iban)).thenReturn(Optional.of(acc));
        when(txRepository.findByCustomerAccountIdAndStatus(
                eq(acc.getId()), eq(TransactionStatus.SETTLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String xml = exportService.generateCamt053(iban);

        assertThat(xml).isNotBlank().contains("BkToCstmrStmt");
    }

    @Test
    @DisplayName("generateCamt053: account not found -> NoSuchElementException")
    void generateCamt053_accountNotFound_throwsNoSuchElement() {
        when(accountRepository.findByIban(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.generateCamt053("DE00000000000000000000"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Account not found");
    }

    // ─── generateDatev ────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateDatev: valid IBAN + 1 settled tx -> CSV with header and data row")
    void generateDatev_oneTx_csvContainsHeaderAndRow() {
        String iban = "DE89370400440532013004";
        CustomerAccount acc = b2bAccount(iban);
        StablecoinTransaction tx = settledTx();

        when(accountRepository.findByIban(iban)).thenReturn(Optional.of(acc));
        when(txRepository.findByCustomerAccountIdAndStatus(
                eq(acc.getId()), eq(TransactionStatus.SETTLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        String csv = exportService.generateDatev(iban);

        assertThat(csv).startsWith("Datum,Belegnummer,Betrag_EUR");
        String[] lines = csv.split("\n");
        assertThat(lines).hasSizeGreaterThanOrEqualTo(2);
        assertThat(lines[1]).contains("2026-01-15").contains("1000.00").contains("USDC");
    }

    @Test
    @DisplayName("generateDatev: account not found -> NoSuchElementException")
    void generateDatev_accountNotFound_throwsNoSuchElement() {
        when(accountRepository.findByIban(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.generateDatev("DE00000000000000000000"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Account not found");
    }
}
