package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.AddressBook;
import de.atruvia.stablecoin.entity.AddressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressBookRepository extends JpaRepository<AddressBook, UUID> {
    List<AddressBook> findByCustomerAccountIdAndStatus(UUID customerAccountId, AddressStatus status);
    Optional<AddressBook> findByCustomerAccountIdAndWalletAddressAndStatus(UUID customerAccountId, String walletAddress, AddressStatus status);
}
