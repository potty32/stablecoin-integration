package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.InstitutionalAddressBook;
import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstitutionalAddressBookRepository extends JpaRepository<InstitutionalAddressBook, UUID> {
    Optional<InstitutionalAddressBook> findByWalletAddressAndStatus(String walletAddress, InstitutionalAddressStatus status);
    List<InstitutionalAddressBook> findByStatus(InstitutionalAddressStatus status);
}
