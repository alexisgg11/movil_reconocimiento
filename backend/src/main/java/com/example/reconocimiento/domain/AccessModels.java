package com.example.reconocimiento.domain;

import java.time.Instant;
import com.example.reconocimiento.domain.Models.Attendance;
import com.example.reconocimiento.domain.Models.Enrollment;
import com.example.reconocimiento.domain.Models.Person;

public final class AccessModels {
    private AccessModels() {}

    public enum Role { ADMINISTRADOR, PERSONA }
    public enum Method { ROSTRO, HUELLA }
    public record Administrator(String dni, String publicKey, String username, String passwordHash) {}
    public record Principal(String dni, Role role) {}
    public record AccessStatus(boolean administradorCreado, boolean preparacionDisponible) {}
    public record LoginRequest(Method metodo, String clave) {}
    public record Credentials(String usuario, String contrasena) {}
    public record InitialChallenge(String codigo, String dni, String clave) {}
    public record InitialAdministrator(String codigo, String dni, String claveDispositivo, Enrollment registro, Credentials credenciales) {}
    public record Session(String token, String dni, Role rol, Instant vence) {}
    public record AttendanceMark(Attendance asistencia, Person persona, boolean yaMarcada) {}
    public record AttendanceReceipt(Attendance asistencia, Person persona, boolean yaMarcada, Session sesion) {}
    public record ProfileUpdate(String nombre, String foto, String version) {}
    public record PublicZone(String nombre, double radio, boolean configurada) {}
}
