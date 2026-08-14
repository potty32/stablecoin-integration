package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.AccountBalanceDto;
import de.atruvia.stablecoin.client.dto.BookingResponseDto;
import de.atruvia.stablecoin.client.dto.CreateHoldDto;
import de.atruvia.stablecoin.client.dto.HoldResponseDto;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;

public interface CoreBankingClient {
    AccountBalanceDto getAccountBalance(String iban);
    HoldResponseDto createHold(String iban, CreateHoldDto request);
    BookingResponseDto createLedgerBooking(LedgerBookingDto request);
}
