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

    /** Tenant-aware Variante — verhindert cross-tenant Ergebnisse wenn RLS nicht greift (z.B. Railway). */
    Optional<CustomerAccount> findByCustomerIdAndTenantId(String customerId, String tenantId);
    Optional<CustomerAccount> findByWalletAddress(String walletAddress);
}
