package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {
    Optional<CustomerAccount> findByIban(String iban);
    Optional<CustomerAccount> findByCustomerId(String customerId);
}
