package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.AccountBalanceDto;
import de.atruvia.stablecoin.client.dto.BookingResponseDto;
import de.atruvia.stablecoin.client.dto.CreateHoldDto;
import de.atruvia.stablecoin.client.dto.HoldResponseDto;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;

public interface CoreBankingClient {
    AccountBalanceDto getAccountBalance(String iban);
    HoldResponseDto createHold(String iban, CreateHoldDto request);
    void releaseHold(String holdId);
    BookingResponseDto createLedgerBooking(LedgerBookingDto request);

    /**
     * G-01: Stornobuchung für eine bereits erstellte Ledger-Buchung.
     * Wird aufgerufen wenn eine Transaktion nach Ledger-Commit fehlschlägt.
     */
    BookingResponseDto reverseBooking(String originalBookingReference, String reason);
}
