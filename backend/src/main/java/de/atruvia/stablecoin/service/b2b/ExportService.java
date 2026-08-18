package de.atruvia.stablecoin.service.b2b;

import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.CustomerType;
import de.atruvia.stablecoin.entity.StablecoinTransaction;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.repository.StablecoinTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates bank-account statement exports:
 * <ul>
 *   <li>CAMT.053.001.08 (ISO 20022 XML) — suitable for ERP/accounting import</li>
 *   <li>DATEV CSV — for German DATEV accounting systems</li>
 * </ul>
 * Only SETTLED transactions are included in both export types.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private static final String CAMT053_NS = "urn:iso:std:iso:20022:tech:xsd:camt.053.001.08";
    private static final String CAMT054_NS = "urn:iso:std:iso:20022:tech:xsd:camt.054.001.08";
    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StablecoinTransactionRepository txRepository;
    private final CustomerAccountRepository accountRepository;

    public ExportService(StablecoinTransactionRepository txRepository,
                         CustomerAccountRepository accountRepository) {
        this.txRepository = txRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Resolves the IBAN to export for. If {@code ibanParam} is blank or null,
     * the first B2B account found is used.
     */
    public String resolveIban(String ibanParam) {
        if (ibanParam != null && !ibanParam.isBlank()) {
            return ibanParam.trim();
        }
        return accountRepository.findAll().stream()
                .filter(a -> CustomerType.B2B.equals(a.getCustomerType()))
                .findFirst()
                .map(CustomerAccount::getIban)
                .orElseThrow(() -> new NoSuchElementException("No B2B account found for export"));
    }

    // ─── CAMT.053 ─────────────────────────────────────────────────────────────

    /**
     * Generates a CAMT.053.001.08-compliant XML statement for all SETTLED
     * transactions of the account identified by {@code iban}.
     *
     * @return UTF-8 XML string
     */
    @Transactional(readOnly = true)
    public String generateCamt053(String iban) {
        CustomerAccount account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + iban));

        List<StablecoinTransaction> transactions = txRepository
                .findByCustomerAccountIdAndStatus(
                        account.getId(), TransactionStatus.SETTLED, Pageable.unpaged())
                .getContent();

        log.info("[Export] CAMT.053 iban={} settledTx={}", iban, transactions.size());

        try {
            return buildCamt053Xml(iban, transactions);
        } catch (Exception e) {
            throw new IllegalStateException("CAMT.053 generation failed: " + e.getMessage(), e);
        }
    }

    // ── CAMT.054 Bank-to-Customer Notification (UC-29: Echtzeit-Avisierung) ─────

    /**
     * Generiert ein CAMT.054.001.08-Dokument mit allen SETTLED INBOUND-Transaktionen
     * für das angegebene IBAN. ERP-Systeme (SAP etc.) nutzen es zur Gutschrift-Verarbeitung.
     */
    public String generateCamt054(String iban) {
        CustomerAccount account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new NoSuchElementException("Konto nicht gefunden: " + iban));
        List<StablecoinTransaction> inboundTxs = txRepository
                .findByCustomerAccountIdAndStatus(account.getId(), TransactionStatus.SETTLED, Pageable.unpaged())
                .getContent()
                .stream()
                .filter(tx -> tx.getType() == TransactionType.INBOUND)
                .toList();
        try {
            return buildCamt054Xml(iban, inboundTxs);
        } catch (Exception e) {
            throw new RuntimeException("CAMT.054-Erzeugung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String buildCamt054Xml(String iban, List<StablecoinTransaction> txList) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        String now = LocalDateTime.now().format(ISO_DT);
        String today = LocalDate.now().format(ISO_DATE);

        Element root = doc.createElementNS(CAMT054_NS, "Document");
        doc.appendChild(root);
        Element msg = el54(doc, root, "BkToCstmrDbtCdtNtfctn");

        // GrpHdr
        Element hdr = el54(doc, msg, "GrpHdr");
        addText(doc, hdr, "MsgId", "CAMT054-" + iban + "-" + System.currentTimeMillis());
        addText(doc, hdr, "CreDtTm", now);
        addText(doc, hdr, "NbOfNtfctns", String.valueOf(txList.isEmpty() ? 0 : 1));

        // Ntfctn
        Element ntf = el54(doc, msg, "Ntfctn");
        addText(doc, ntf, "Id", "NTFCTN-" + iban);
        addText(doc, ntf, "CreDtTm", now);

        Element acct = el54(doc, ntf, "Acct");
        Element acctId = el54(doc, acct, "Id");
        addText(doc, acctId, "IBAN", iban);

        for (StablecoinTransaction tx : txList) {
            String bookDate = tx.getSettledAt() != null
                    ? tx.getSettledAt().format(ISO_DT)
                    : now;
            String valDate = tx.getSettledAt() != null
                    ? tx.getSettledAt().toLocalDate().format(ISO_DATE)
                    : today;

            Element ntry = el54(doc, ntf, "Ntry");

            // Betrag in EUR
            Element amt = doc.createElementNS(CAMT054_NS, "Amt");
            amt.setAttribute("Ccy", "EUR");
            amt.setTextContent(tx.getAmountFiat() != null
                    ? tx.getAmountFiat().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    : "0.00");
            ntry.appendChild(amt);

            addText(doc, ntry, "CdtDbtInd", "CRDT");        // Gutschrift
            Element sts = el54(doc, ntry, "Sts");
            addText(doc, sts, "Cd", "BOOK");                  // gebucht
            Element bookgDt = el54(doc, ntry, "BookgDt");
            addText(doc, bookgDt, "DtTm", bookDate);
            Element valDt = el54(doc, ntry, "ValDt");
            addText(doc, valDt, "Dt", valDate);
            addText(doc, ntry, "NtryRef", tx.getId().toString());

            // Bank Transaction Code: PMNT/RCDT/ESCT (Received Credit Transfer)
            Element bkTxCd = el54(doc, ntry, "BkTxCd");
            Element domn = el54(doc, bkTxCd, "Domn");
            addText(doc, domn, "Cd", "PMNT");
            Element fmly = el54(doc, domn, "Fmly");
            addText(doc, fmly, "Cd", "RCDT");
            addText(doc, fmly, "SubFmlyCd", "ESCT");

            // NtryDtls
            Element ntryDtls = el54(doc, ntry, "NtryDtls");
            Element txDtls = el54(doc, ntryDtls, "TxDtls");
            Element refs = el54(doc, txDtls, "Refs");
            addText(doc, refs, "EndToEndId", tx.getId().toString());
            if (tx.getBlockchainHash() != null) {
                addText(doc, txDtls, "AddtlTxInf", tx.getBlockchainHash());
            }
        }

        // XML serialisieren
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        java.io.StringWriter sw = new java.io.StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    private Element el54(Document doc, Element parent, String tag) {
        Element el = doc.createElementNS(CAMT054_NS, tag);
        parent.appendChild(el);
        return el;
    }

    // ── CAMT.053 ──────────────────────────────────────────────────────────────

    private String buildCamt053Xml(String iban, List<StablecoinTransaction> txList) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        // ── Document ──────────────────────────────────────────────────────────
        Element document = doc.createElementNS(CAMT053_NS, "Document");
        document.setAttribute("xmlns", CAMT053_NS);
        doc.appendChild(document);

        // ── BkToCstmrStmt ─────────────────────────────────────────────────────
        Element bkToCstmrStmt = el(doc, "BkToCstmrStmt");
        document.appendChild(bkToCstmrStmt);

        // ── GrpHdr ────────────────────────────────────────────────────────────
        Element grpHdr = el(doc, "GrpHdr");
        bkToCstmrStmt.appendChild(grpHdr);
        addText(doc, grpHdr, "MsgId", "CAMT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        addText(doc, grpHdr, "CreDtTm", LocalDateTime.now().format(ISO_DT));

        // ── Stmt ──────────────────────────────────────────────────────────────
        Element stmt = el(doc, "Stmt");
        bkToCstmrStmt.appendChild(stmt);
        addText(doc, stmt, "Id", "STMT-" + LocalDate.now().format(ISO_DATE));
        addText(doc, stmt, "CreDtTm", LocalDateTime.now().format(ISO_DT));

        // Acct
        Element acct = el(doc, "Acct");
        stmt.appendChild(acct);
        Element acctId = el(doc, "Id");
        acct.appendChild(acctId);
        addText(doc, acctId, "IBAN", iban);

        // Bal (Closing Booked — required by CAMT.053)
        BigDecimal totalAmt = txList.stream()
                .map(StablecoinTransaction::getAmountFiat)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Element bal = el(doc, "Bal");
        stmt.appendChild(bal);
        Element balTp = el(doc, "Tp");
        bal.appendChild(balTp);
        Element cdOrPrtry = el(doc, "CdOrPrtry");
        balTp.appendChild(cdOrPrtry);
        addText(doc, cdOrPrtry, "Cd", "CLBD");

        Element balAmt = doc.createElementNS(CAMT053_NS, "Amt");
        balAmt.setAttribute("Ccy", "EUR");
        balAmt.setTextContent(totalAmt.toPlainString());
        bal.appendChild(balAmt);

        addText(doc, bal, "CdtDbtInd", "DBIT");
        Element balDt = el(doc, "Dt");
        bal.appendChild(balDt);
        addText(doc, balDt, "Dt", LocalDate.now().format(ISO_DATE));

        // ── Ntry per transaction ───────────────────────────────────────────────
        for (StablecoinTransaction tx : txList) {
            Element ntry = el(doc, "Ntry");
            stmt.appendChild(ntry);

            // Amt
            BigDecimal amt = tx.getAmountFiat() != null
                    ? tx.getAmountFiat().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
            Element ntryAmt = doc.createElementNS(CAMT053_NS, "Amt");
            ntryAmt.setAttribute("Ccy", "EUR");
            ntryAmt.setTextContent(amt.toPlainString());
            ntry.appendChild(ntryAmt);

            addText(doc, ntry, "CdtDbtInd", "DBIT");

            // Sts (EntryStatus4Code in 001.08 uses Cd sub-element)
            Element sts = el(doc, "Sts");
            ntry.appendChild(sts);
            addText(doc, sts, "Cd", "BOOK");

            // BookgDt
            Element bookgDt = el(doc, "BookgDt");
            ntry.appendChild(bookgDt);
            LocalDate bookDate = tx.getSettledAt() != null
                    ? tx.getSettledAt().toLocalDate()
                    : LocalDate.now();
            addText(doc, bookgDt, "Dt", bookDate.format(ISO_DATE));

            addText(doc, ntry, "NtryRef", tx.getId().toString());

            // BkTxCd
            Element bkTxCd = el(doc, "BkTxCd");
            ntry.appendChild(bkTxCd);
            Element domn = el(doc, "Domn");
            bkTxCd.appendChild(domn);
            addText(doc, domn, "Cd", "PMNT");
            Element fmly = el(doc, "Fmly");
            domn.appendChild(fmly);
            addText(doc, fmly, "Cd", "ICDT");
            addText(doc, fmly, "SubFmlyCd", "ESCT");

            // NtryDtls / TxDtls / Refs
            Element ntryDtls = el(doc, "NtryDtls");
            ntry.appendChild(ntryDtls);
            Element txDtls = el(doc, "TxDtls");
            ntryDtls.appendChild(txDtls);
            Element refs = el(doc, "Refs");
            txDtls.appendChild(refs);
            addText(doc, refs, "EndToEndId", tx.getId().toString());

            // AddtlNtryInf at Ntry level (ISO 20022 compliant position)
            String hash = tx.getBlockchainHash() != null ? tx.getBlockchainHash() : "N/A";
            addText(doc, ntry, "AddtlNtryInf", hash);
        }

        // ── Serialize ─────────────────────────────────────────────────────────
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter sw = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    // ─── DATEV CSV ────────────────────────────────────────────────────────────

    /**
     * Generates a DATEV-compatible CSV for all SETTLED transactions.
     * Header: Datum,Belegnummer,Betrag_EUR,Betrag_Stablecoin,Waehrung,Blockchain_Hash,Bruttoertrag_EUR,Status
     *
     * @return CSV string (UTF-8)
     */
    @Transactional(readOnly = true)
    public String generateDatev(String iban) {
        CustomerAccount account = accountRepository.findByIban(iban)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + iban));

        List<StablecoinTransaction> transactions = txRepository
                .findByCustomerAccountIdAndStatus(
                        account.getId(), TransactionStatus.SETTLED, Pageable.unpaged())
                .getContent();

        log.info("[Export] DATEV iban={} settledTx={}", iban, transactions.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Datum,Belegnummer,Betrag_EUR,Betrag_Stablecoin,Waehrung,Blockchain_Hash,Bruttoertrag_EUR,Status\n");

        for (StablecoinTransaction tx : transactions) {
            String datum = tx.getSettledAt() != null
                    ? tx.getSettledAt().toLocalDate().format(ISO_DATE)
                    : (tx.getCreatedAt() != null ? tx.getCreatedAt().toLocalDate().format(ISO_DATE) : "");
            String betragEur = tx.getAmountFiat() != null ? tx.getAmountFiat().toPlainString() : "0";
            String betragStablecoin = tx.getAmountStablecoin() != null ? tx.getAmountStablecoin().toPlainString() : "0";
            String waehrung = tx.getCurrency() != null ? tx.getCurrency().name() : "";
            String hash = tx.getBlockchainHash() != null ? tx.getBlockchainHash() : "";
            String bruttoertrag = tx.getGrossRevenue() != null ? tx.getGrossRevenue().toPlainString() : "0";
            String status = tx.getStatus() != null ? tx.getStatus().name() : "";

            sb.append(csvEscape(datum)).append(',')
              .append(csvEscape(tx.getId().toString())).append(',')
              .append(csvEscape(betragEur)).append(',')
              .append(csvEscape(betragStablecoin)).append(',')
              .append(csvEscape(waehrung)).append(',')
              .append(csvEscape(hash)).append(',')
              .append(csvEscape(bruttoertrag)).append(',')
              .append(csvEscape(status)).append('\n');
        }

        return sb.toString();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Wraps values containing commas or quotes in double-quotes. */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Creates a namespace-aware element in the CAMT.053 namespace. */
    private Element el(Document doc, String localName) {
        return doc.createElementNS(CAMT053_NS, localName);
    }

    /** Creates a child element with text content and appends it to {@code parent}. */
    private void addText(Document doc, Element parent, String localName, String text) {
        Element child = el(doc, localName);
        child.setTextContent(text);
        parent.appendChild(child);
    }
}
