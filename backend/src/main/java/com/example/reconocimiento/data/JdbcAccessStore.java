package com.example.reconocimiento.data;

import com.example.reconocimiento.domain.AccessStore;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.example.reconocimiento.domain.AccessModels.*;

@Repository
public class JdbcAccessStore implements AccessStore {
    private final JdbcTemplate db;

    public JdbcAccessStore(JdbcTemplate db) { this.db = db; }

    @Override public Administrator administrator() {
        return db.query("SELECT dni,clave_dispositivo,nombre_usuario,clave_hash FROM reconocimiento.usuario WHERE id=1",
                (row, index) -> new Administrator(row.getString(1), row.getString(2), row.getString(3), row.getString(4)))
                .stream().findFirst().orElse(null);
    }

    @Override public void createAdministrator(Administrator account) {
        db.update("INSERT INTO reconocimiento.usuario(id,dni,rol,clave_dispositivo,nombre_usuario,clave_hash) VALUES(1,?,'ADMINISTRADOR',?,?,?)",
                account.dni(), account.publicKey(), account.username(), account.passwordHash());
    }

    @Override public void updateAdministrator(Administrator account) {
        db.update("UPDATE reconocimiento.usuario SET clave_dispositivo=?,nombre_usuario=?,clave_hash=? WHERE id=1 AND dni=?",
                account.publicKey(), account.username(), account.passwordHash(), account.dni());
    }

    @Override public void createSession(String tokenHash, Principal principal, Instant expiresAt, Instant now) {
        db.update("DELETE FROM reconocimiento.sesion WHERE vence<=?", Timestamp.from(now));
        db.update("INSERT INTO reconocimiento.sesion(token_hash,dni,rol,vence) VALUES(?,?,?,?)",
                tokenHash, principal.dni(), principal.role().name(), Timestamp.from(expiresAt));
    }

    @Override public Principal session(String tokenHash, Instant now) {
        return db.query("""
                SELECT s.dni,s.rol FROM reconocimiento.sesion s
                JOIN reconocimiento.persona p ON p.dni=s.dni AND p.activo=TRUE
                WHERE s.token_hash=? AND s.vence>?
                  AND (s.rol='PERSONA' OR EXISTS(SELECT 1 FROM reconocimiento.usuario u WHERE u.dni=s.dni AND u.rol=s.rol))
                """, (row,index) -> new Principal(row.getString(1), Role.valueOf(row.getString(2))),
                tokenHash, Timestamp.from(now)).stream().findFirst().orElse(null);
    }

    @Override public void deleteSession(String tokenHash) {
        db.update("DELETE FROM reconocimiento.sesion WHERE token_hash=?", tokenHash);
    }

    @Override public void revokeSessions(String dni) {
        db.update("DELETE FROM reconocimiento.sesion WHERE dni=?", dni);
    }
}
