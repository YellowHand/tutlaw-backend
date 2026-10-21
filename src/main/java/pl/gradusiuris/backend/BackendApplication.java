package pl.gradusiuris.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// Doba w aplikacji (dzienny limit nowych kart, terminy powtórek, wykres aktywności) liczy się
		// w czasie polskim niezależnie od strefy serwera. Daty w bazie to czas lokalny bez strefy.
		String zone = System.getenv().getOrDefault("APP_TIMEZONE", "Europe/Warsaw");
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)));

		SpringApplication.run(BackendApplication.class, args);
	}

}
