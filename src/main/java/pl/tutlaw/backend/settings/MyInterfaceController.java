package pl.tutlaw.backend.settings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.settings.dto.InterfaceSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateInterfaceRequest;

/** Ustawienia interfejsu na koncie: podpowiedzi skrótów klawiszowych i licznik czasu pytania. */
@RestController
@RequestMapping("/api/me/interface")
@RequiredArgsConstructor
public class MyInterfaceController {

    private final InterfaceSettingsService service;

    @GetMapping
    public InterfaceSettingsResponse get(Authentication authentication) {
        return service.get(authentication);
    }

    @PutMapping
    public InterfaceSettingsResponse update(@Valid @RequestBody UpdateInterfaceRequest request, Authentication authentication) {
        return service.update(request, authentication);
    }
}
