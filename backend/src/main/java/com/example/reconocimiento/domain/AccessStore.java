package com.example.reconocimiento.domain;

import java.time.Instant;
import static com.example.reconocimiento.domain.AccessModels.*;

public interface AccessStore {
    Administrator administrator();
    void createAdministrator(Administrator administrator);
    void updateAdministrator(Administrator administrator);
    void createSession(String tokenHash, Principal principal, Instant expiresAt, Instant now);
    Principal session(String tokenHash, Instant now);
    void deleteSession(String tokenHash);
    void revokeSessions(String dni);
}
