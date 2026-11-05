package com.example.reconocimiento.api;

import com.example.reconocimiento.application.AccessService;
import com.example.reconocimiento.application.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.example.reconocimiento.api.AccessGate.*;
import static com.example.reconocimiento.domain.AccessModels.*;
import static com.example.reconocimiento.domain.Models.*;

@RestController
@RequestMapping("/api")
public class AccessController {
    private final AccessService access;
    private final AttendanceService attendance;

    public AccessController(AccessService access, AttendanceService attendance) {
        this.access = access;
        this.attendance = attendance;
    }

    @Allow(Level.PUBLIC)
    @GetMapping("/acceso/estado")
    public AccessStatus status() { return access.status(); }

    @Allow(Level.PUBLIC)
    @GetMapping("/asistencia/zona")
    public PublicZone zone() {
        var site = attendance.configuration();
        return new PublicZone(site.nombre(), site.radio(), site.latitud() != null && site.longitud() != null);
    }

    public record LoginChallenge(String id, String nonce, String proposito) {
        static LoginChallenge from(Challenge challenge) {
            return new LoginChallenge(challenge.id(), challenge.nonce(), challenge.proposito());
        }
    }

    @Allow(Level.PUBLIC)
    @PostMapping("/acceso/retos")
    public LoginChallenge challenge(@RequestBody LoginRequest request, HttpServletRequest http) {
        return LoginChallenge.from(access.challenge(request, http.getRemoteAddr()));
    }

    @Allow(Level.PUBLIC)
    @PostMapping("/acceso/sesion")
    public Session login(@RequestBody Proof proof, HttpServletRequest http) {
        return access.login(proof, http.getRemoteAddr());
    }

    @Allow(Level.PUBLIC)
    @PostMapping("/acceso/credenciales")
    public Session loginWithCredentials(@RequestBody Credentials credentials) {
        return access.loginWithCredentials(credentials);
    }

    @Allow(Level.PUBLIC)
    @DeleteMapping("/acceso/sesion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        access.logout(authorization);
    }

    @Allow(Level.PUBLIC)
    @PostMapping("/acceso/preparacion/reto")
    public LoginChallenge initialFingerprint(@RequestBody InitialChallenge request, HttpServletRequest http) {
        return LoginChallenge.from(access.initialFingerprint(request, http.getRemoteAddr()));
    }

    @Allow(Level.PUBLIC)
    @PostMapping("/acceso/preparacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prepare(@RequestBody InitialAdministrator request, HttpServletRequest http) {
        access.prepareAdministrator(request, http.getRemoteAddr());
    }

    @Allow(Level.SESSION)
    @GetMapping("/mi-ficha")
    public Person profile(@RequestAttribute(PRINCIPAL) Principal principal) {
        return access.profile(principal);
    }

    @Allow(Level.SESSION)
    @PutMapping("/mi-ficha")
    public Person update(@RequestAttribute(PRINCIPAL) Principal principal, @RequestBody ProfileUpdate update, HttpServletRequest http) {
        return access.updateProfile(principal, update, http.getRemoteAddr());
    }
}
