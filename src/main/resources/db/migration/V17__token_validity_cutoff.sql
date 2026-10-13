-- Etap 15: unieważnianie sesji. Token JWT ma czas wystawienia (claim iat, sekundy od 1970). Jeśli konto ma
-- ustawione tokens_valid_from, tokeny wystawione wcześniej przestają działać. Ustawiamy je przy zmianie hasła
-- i przy resecie hasła, więc skradziona sesja nie przeżywa zmiany hasła.
-- NULL = brak ograniczenia (wszystkie istniejące konta zaczynają bez niego, więc nikt nie zostaje wylogowany).
-- Sekundy zamiast TIMESTAMP, żeby nie mieszać stref czasowych z czasem w tokenie.
ALTER TABLE users ADD COLUMN tokens_valid_from BIGINT;
