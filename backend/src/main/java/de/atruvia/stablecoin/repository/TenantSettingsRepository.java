package de.atruvia.stablecoin.repository;

import de.atruvia.stablecoin.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {}
