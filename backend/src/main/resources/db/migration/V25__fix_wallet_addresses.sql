-- V25: Wallet-Adressen auf gültige Ethereum-Länge korrigieren (0x + 40 Hex = 42 Zeichen)
-- Ethereum: nur Hex-Zeichen erlaubt (0-9, a-f, A-F). Keine Buchstaben wie U, R, G, K.

-- address_book — tenant-kleine-vb
UPDATE address_book SET wallet_address = '0xA100000000000000000000000000000000000001'
  WHERE label = 'Hauptpartner GmbH (USDC)';

UPDATE address_book SET wallet_address = '0xA200000000000000000000000000000000000001'
  WHERE label = 'EU-Lieferant (EURC)';

-- address_book — tenant-grosse-vb
UPDATE address_book SET wallet_address = '0xB100000000000000000000000000000000000001'
  WHERE label = 'Metropole Partner AG (USDC)';

UPDATE address_book SET wallet_address = '0xB200000000000000000000000000000000000001'
  WHERE label = 'Interbanken-EURC Empfänger';

-- institutional_address_book
UPDATE institutional_address_book
  SET wallet_address = '0xDB00000000000000000000000000000000000001'
  WHERE label = 'Deutsche Bank USDC Custody';

UPDATE institutional_address_book
  SET wallet_address = '0xD200000000000000000000000000000000000001'
  WHERE label = 'DZ Bank EURC Settlement Wallet';

-- Prüfung: alle müssen jetzt 42 Zeichen haben
-- 0xDEAD000000000000000000000000000000000000 = 42 ✓ (Sanktionstest, unverändert)
