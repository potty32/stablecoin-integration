package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.PhoneAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhoneAliasRepository extends JpaRepository<PhoneAlias, UUID> {
    Optional<PhoneAlias> findByPhoneNumberHash(String phoneNumberHash);
}
