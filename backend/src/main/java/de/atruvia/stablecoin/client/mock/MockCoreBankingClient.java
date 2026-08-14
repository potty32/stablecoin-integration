package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.CoreBankingClient;
import de.atruvia.stablecoin.client.dto.AccountBalanceDto;
import de.atruvia.stablecoin.client.dto.BookingResponseDto;
import de.atruvia.stablecoin.client.dto.CreateHoldDto;
import de.atruvia.stablecoin.client.dto.HoldResponseDto;
import de.atruvia.stablecoin.client.dto.LedgerBookingDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("dev")
public class MockCoreBankingClient implements CoreBankingClient {

    private static final Map<String, BigDecimal> BALANCES = Map.of(
            "DE89370400440532013000", new BigDecimal("250000.00"),
            "DE27200400600532013001", new BigDecimal("3500.00")
    );

    @Override
    public AccountBalanceDto getAccountBalance(String iban) {
        BigDecimal balance = BALANCES.getOrDefault(iban, new BigDecimal("10000.00"));
        return new AccountBalanceDto(iban, balance, "EUR");
    }

    @Override
    public HoldResponseDto createHold(String iban, CreateHoldDto request) {
        String holdId = "hold-" + UUID.randomUUID().toString().substring(0, 8);
        return new HoldResponseDto(holdId, iban, request.amount(), "HELD", LocalDateTime.now().plusHours(2));
    }

    @Override
    public BookingResponseDto createLedgerBooking(LedgerBookingDto request) {
        String bookingId = "booking-" + UUID.randomUUID().toString().substring(0, 8);
        return new BookingResponseDto(bookingId, "BOOKED", LocalDateTime.now());
    }
}
