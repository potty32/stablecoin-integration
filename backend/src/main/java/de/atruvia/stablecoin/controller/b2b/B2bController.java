package de.atruvia.stablecoin.controller.b2b;

import de.atruvia.stablecoin.dto.request.b2b.AddAddressRequest;
import de.atruvia.stablecoin.dto.request.b2b.ApproveTransferRequest;
import de.atruvia.stablecoin.dto.request.b2b.DvpCancelRequest;
import de.atruvia.stablecoin.dto.request.b2b.DvpLockRequest;
import de.atruvia.stablecoin.dto.request.b2b.DvpSettleRequest;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.dto.response.AddressBookResponse;
import de.atruvia.stablecoin.dto.response.BulkPaymentResult;
import de.atruvia.stablecoin.dto.response.DvpEscrowResponse;
import de.atruvia.stablecoin.dto.response.RateQuoteResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.dto.response.TransferPageResponse;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.dto.request.b2b.AddInstitutionalAddressRequest;
import de.atruvia.stablecoin.dto.response.InstitutionalAddressBookResponse;
import de.atruvia.stablecoin.dto.request.ReassignTransactionRequest;
import de.atruvia.stablecoin.dto.request.b2b.KillSwitchRequest;
import de.atruvia.stablecoin.service.b2b.AddressBookService;
import de.atruvia.stablecoin.service.b2b.DvpEscrowService;
import de.atruvia.stablecoin.service.b2b.KillSwitchService;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.b2b.BulkPaymentService;
import de.atruvia.stablecoin.service.b2b.ExportService;
import de.atruvia.stablecoin.service.b2b.InstitutionalAddressBookService;
import de.atruvia.stablecoin.service.b2b.ReassignTransactionService;
import de.atruvia.stablecoin.service.b2b.SanctionsBatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/b2b")
@Validated
public class B2bController {

    private final B2bTransferService transferService;
    private final AddressBookService addressBookService;
    private final BulkPaymentService bulkPaymentService;
    private final ExportService exportService;
    private final SanctionsBatchService sanctionsBatchService;
    private final ReassignTransactionService reassignTransactionService;
    private final KillSwitchService killSwitchService;
    private final InstitutionalAddressBookService institutionalAddressBookService;
    private final DvpEscrowService dvpEscrowService;

    public B2bController(B2bTransferService transferService,
                         AddressBookService addressBookService,
                         BulkPaymentService bulkPaymentService,
                         ExportService exportService,
                         SanctionsBatchService sanctionsBatchService,
                         ReassignTransactionService reassignTransactionService,
                         KillSwitchService killSwitchService,
                         InstitutionalAddressBookService institutionalAddressBookService,
                         DvpEscrowService dvpEscrowService) {
        this.transferService                = transferService;
        this.addressBookService             = addressBookService;
        this.bulkPaymentService             = bulkPaymentService;
        this.exportService                  = exportService;
        this.sanctionsBatchService          = sanctionsBatchService;
        this.reassignTransactionService     = reassignTransactionService;
        this.killSwitchService              = killSwitchService;
        this.institutionalAddressBookService = institutionalAddressBookService;
        this.dvpEscrowService               = dvpEscrowService;
    }

    // ─── Transfer endpoints ───────────────────────────────────────────────────

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> initiateTransfer(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid InitiateTransferRequest request,
            Authentication auth) {
        TransactionResponse response = transferService.initiate(idempotencyKey, request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transfers")
    public ResponseEntity<TransferPageResponse> listTransfers(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        TransferPageResponse result = transferService.listTransfers(auth.getName(), status, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<TransactionResponse> getTransfer(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(transferService.getById(id));
    }

    @PostMapping("/transfers/{id}/approve")
    public ResponseEntity<TransactionResponse> approveTransfer(
            @PathVariable UUID id,
            @RequestBody ApproveTransferRequest request,
            Authentication auth) {
        // approverId kommt aus dem JWT — Request-Body-Wert wird ignoriert
        return ResponseEntity.ok(transferService.approve(id, new ApproveTransferRequest(auth.getName())));
    }

    @PostMapping("/transfers/{id}/reject")
    public ResponseEntity<TransactionResponse> rejectTransfer(
            @PathVariable UUID id,
            @RequestBody ApproveTransferRequest request,
            Authentication auth) {
        // approverId kommt aus dem JWT — Request-Body-Wert wird ignoriert
        return ResponseEntity.ok(transferService.reject(id, new ApproveTransferRequest(auth.getName())));
    }

    @GetMapping("/rate-quote")
    public ResponseEntity<RateQuoteResponse> getRateQuote(
            @RequestParam BigDecimal amountEur,
            @RequestParam StablecoinCurrency currency,
            Authentication auth) {
        return ResponseEntity.ok(transferService.createRateQuote(amountEur, currency, auth.getName()));
    }

    // ─── Address-book endpoints ───────────────────────────────────────────────

    @PostMapping("/address-book")
    public ResponseEntity<AddressBookResponse> addAddress(
            @RequestBody @Valid AddAddressRequest request,
            Authentication auth) {
        AddressBookResponse response = addressBookService.addAddress(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/address-book")
    public ResponseEntity<List<AddressBookResponse>> listAddresses(Authentication auth) {
        return ResponseEntity.ok(addressBookService.listAddresses(auth.getName()));
    }

    @DeleteMapping("/address-book/{id}")
    public ResponseEntity<Void> revokeAddress(@PathVariable UUID id, Authentication auth) {
        addressBookService.revokeAddress(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ─── Phase 4: Bulk payments ───────────────────────────────────────────────

    /**
     * Accepts a CSV file and creates one transfer per row.
     * CSV format: destinationWallet,amountEur,currency,reference
     * <p>
     * Required header: {@code X-Idempotency-Key} (logged but not used for
     * deduplication of the batch itself — each row gets its own random key).
     * Required param:  {@code sourceIban}
     */
    @PostMapping(value = "/bulk-payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BulkPaymentResult> bulkPayments(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestParam String sourceIban,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        BulkPaymentResult result = bulkPaymentService.process(file, sourceIban, auth.getName());
        return ResponseEntity.ok(result);
    }

    // ─── Phase 4: Exports ─────────────────────────────────────────────────────

    /**
     * Downloads SETTLED transactions as an ISO 20022 CAMT.053.001.08 XML file.
     * Optional query param {@code iban}; defaults to the first available B2B IBAN.
     */
    @GetMapping("/export/camt053")
    public ResponseEntity<byte[]> exportCamt053(
            @RequestParam(required = false) String iban,
            Authentication auth) {
        String resolvedIban = exportService.resolveIban(iban);
        String xml          = exportService.generateCamt053(resolvedIban);
        String filename     = "camt053-" + resolvedIban + ".xml";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * UC-29: Echtzeit-Avisierung — CAMT.054.001.08 Bank-to-Customer Notification.
     * Enthält alle einlaufenden Gutschriften (INBOUND, SETTLED) für ERP-Systeme (z.B. SAP).
     * Optional query param {@code iban}; defaults to the first available B2B IBAN.
     */
    @GetMapping("/export/camt054")
    public ResponseEntity<byte[]> exportCamt054(
            @RequestParam(required = false) String iban,
            Authentication auth) {
        String resolvedIban = exportService.resolveIban(iban);
        String xml          = exportService.generateCamt054(resolvedIban);
        String filename     = "camt054-" + resolvedIban + ".xml";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    // ── Async S3-Export (Zielbild) ────────────────────────────────────────────────

    /**
     * Triggert asynchronen Export → S3-Ablage → Presigned-URL zurück.
     * Typ: camt053 | camt054 | camt029 | datev
     * URL gültig: 15 Minuten (konfigurierbar in ExportService)
     */
    @PostMapping("/export/async-trigger")
    public ResponseEntity<java.util.Map<String, Object>> triggerAsyncExport(
            @RequestParam String type,
            @RequestParam(required = false) String iban,
            Authentication auth) {
        String tenantId = de.atruvia.stablecoin.config.TenantContext.get();
        String presignedUrl = exportService.triggerAsyncExport(type, iban, tenantId != null ? tenantId : "default");
        return ResponseEntity.accepted().body(java.util.Map.of(
                "presignedUrl", presignedUrl,
                "validForSeconds", 900,
                "exportType", type,
                "message", "Export generiert und in S3 abgelegt. URL gültig 15 Minuten."));
    }

    /**
     * G-10: CAMT.029 Rejection-Benachrichtigung für automatische Retouren (INBOUND_RETURN).
     * ISO 20022 Unable-To-Apply Notification — ERP-Systeme verbuchen fehlgeschlagene Eingänge.
     * Optional: since-Parameter (ISO-8601 DateTime, Default: letzte 24h).
     */
    @GetMapping("/export/camt029")
    public ResponseEntity<byte[]> exportCamt029(
            @RequestParam(required = false) String iban,
            @RequestParam(required = false) String since,
            Authentication auth) {
        String resolvedIban = exportService.resolveIban(iban);
        java.time.LocalDateTime sinceDateTime = since != null
                ? java.time.LocalDateTime.parse(since)
                : java.time.LocalDateTime.now().minusHours(24);
        String xml      = exportService.generateCamt029(resolvedIban, sinceDateTime);
        String filename = "camt029-" + resolvedIban + ".xml";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Downloads SETTLED transactions as a DATEV-compatible CSV file.
     * Optional query param {@code iban}; defaults to the first available B2B IBAN.
     */
    @GetMapping("/export/datev")
    public ResponseEntity<byte[]> exportDatev(
            @RequestParam(required = false) String iban,
            Authentication auth) {
        String resolvedIban = exportService.resolveIban(iban);
        String csv          = exportService.generateDatev(resolvedIban);
        String date         = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String filename     = "datev-export-" + resolvedIban + "-" + date + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/admin/sanctions-scan")
    public ResponseEntity<String> triggerSanctionsScan(Authentication auth) {
        sanctionsBatchService.runNightlySanctionsScan();
        return ResponseEntity.ok("{\"message\":\"Sanctions scan completed\"}");
    }

    // ── G-07: Kill Switch Admin-Endpoints ────────────────────────────────────

    @PostMapping("/admin/kill-switch/activate")
    public ResponseEntity<String> activateKillSwitch(
            @RequestBody KillSwitchRequest request, Authentication auth) {
        if ("GLOBAL".equalsIgnoreCase(request.scope())) {
            killSwitchService.activateGlobal(request.reason(), auth.getName());
            return ResponseEntity.ok("{\"message\":\"Globaler Kill Switch aktiviert\"}");
        }
        killSwitchService.activateTenant(request.scope(), request.reason(), auth.getName());
        return ResponseEntity.ok("{\"message\":\"Mandanten Kill Switch aktiviert: " + request.scope() + "\"}");
    }

    @PostMapping("/admin/kill-switch/deactivate")
    public ResponseEntity<String> deactivateKillSwitch(
            @RequestBody KillSwitchRequest request, Authentication auth) {
        if ("GLOBAL".equalsIgnoreCase(request.scope())) {
            killSwitchService.deactivateGlobal(auth.getName());
            return ResponseEntity.ok("{\"message\":\"Globaler Kill Switch deaktiviert\"}");
        }
        killSwitchService.deactivateTenant(request.scope(), auth.getName());
        return ResponseEntity.ok("{\"message\":\"Mandanten Kill Switch deaktiviert: " + request.scope() + "\"}");
    }

    @GetMapping("/admin/kill-switch/status")
    public ResponseEntity<Object> killSwitchStatus() {
        return ResponseEntity.ok(killSwitchService.getGlobalStatus());
    }

    /**
     * UC-31: Manuelle Sammelkonto-Bereinigung.
     * Ordnet eine UNASSIGNED-Transaktion einem echten Kundenkonto zu
     * und stößt die nachträgliche Ledger-Gutschrift an.
     */
    @PostMapping("/admin/reassign-transaction")
    public ResponseEntity<TransactionResponse> reassignTransaction(
            @RequestBody ReassignTransactionRequest request) {
        return ResponseEntity.ok(reassignTransactionService.reassign(request));
    }

    @PostMapping("/institutional-address-book")
    public ResponseEntity<InstitutionalAddressBookResponse> addInstitutionalAddress(
            @RequestBody @Valid AddInstitutionalAddressRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(institutionalAddressBookService.addAddress(request, auth.getName()));
    }

    @GetMapping("/institutional-address-book")
    public ResponseEntity<List<InstitutionalAddressBookResponse>> listInstitutionalAddresses(Authentication auth) {
        return ResponseEntity.ok(institutionalAddressBookService.listAddresses());
    }

    @DeleteMapping("/institutional-address-book/{id}")
    public ResponseEntity<Void> revokeInstitutionalAddress(
            @PathVariable UUID id,
            Authentication auth) {
        institutionalAddressBookService.revokeAddress(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ── UC-33/34/35: Delivery-versus-Payment (DvP) ──────────────────────────

    /**
     * UC-33: Sperrt einen Stablecoin-Betrag im DvP-Escrow für ein Wertpapiergeschäft.
     * Eliminiert Herstatt-Risiko durch atomare Sperre vor der Wertpapierübertragung.
     */
    @PostMapping("/dvp/lock")
    public ResponseEntity<DvpEscrowResponse> dvpLock(
            @RequestBody @Valid DvpLockRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dvpEscrowService.lock(request, auth.getName()));
    }

    /**
     * UC-34: Gibt gesperrte Stablecoins frei, sobald das Wertpapier übertragen wurde.
     * Wird von der Wertpapierabwicklung (Deka/Union/Clearstream) getriggert.
     */
    @PostMapping("/dvp/settle")
    public ResponseEntity<DvpEscrowResponse> dvpSettle(
            @RequestBody @Valid DvpSettleRequest request,
            Authentication auth) {
        return ResponseEntity.ok(dvpEscrowService.settle(request, auth.getName()));
    }

    /**
     * UC-35: Löst den Escrow auf und gibt den gesperrten Betrag gebührenfrei zurück.
     * Wird aufgerufen wenn die Wertpapierübertragung gescheitert ist.
     */
    @PostMapping("/dvp/cancel")
    public ResponseEntity<DvpEscrowResponse> dvpCancel(
            @RequestBody @Valid DvpCancelRequest request,
            Authentication auth) {
        return ResponseEntity.ok(dvpEscrowService.cancel(request, auth.getName()));
    }
}
