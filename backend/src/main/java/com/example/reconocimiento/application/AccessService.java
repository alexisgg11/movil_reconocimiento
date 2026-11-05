package com.example.reconocimiento.application;

import com.example.reconocimiento.domain.AccessStore;
import com.example.reconocimiento.domain.AttendanceStore;
import com.example.reconocimiento.domain.RuleViolation;
import com.example.reconocimiento.domain.Rules;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.reconocimiento.domain.AccessModels.*;
import static com.example.reconocimiento.domain.Models.*;
import static com.example.reconocimiento.domain.Rules.require;

@Service
public class AccessService {
    private static final String LOGIN_PREFIX = "LOGIN_";
    private static final String ADMIN_LOGIN_PREFIX = LOGIN_PREFIX + "ADMINISTRADOR_";
    private static final Duration CHALLENGE_DURATION = Duration.ofMinutes(3);
    private final AttendanceStore people;
    private final AccessStore access;
    private final AttendanceService attendance;
    private final Clock clock;
    private final String setupCode;
    private final SecureRandom random = new SecureRandom();
    private Instant attemptWindow = Instant.MIN;
    private int attempts;

    public AccessService(AttendanceStore people, AccessStore access, AttendanceService attendance,
                         Clock clock, @Value("${ADMIN_SETUP_CODE:}") String setupCode) {
        this.people = people;
        this.access = access;
        this.attendance = attendance;
        this.clock = clock;
        this.setupCode = setupCode;
    }

    public AccessStatus status() {
        return new AccessStatus(access.administrator() != null, setupEnabled());
    }

    private boolean setupEnabled() { return setupCode.matches("[0-9a-fA-F]{32}"); }

    private void checkSetupCode(String code) {
        if (!setupEnabled() || code == null || !code.matches("[0-9a-fA-F]{32}") ||
                !MessageDigest.isEqual(setupCode.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
            throw new RuleViolation("PREPARACION_NO_AUTORIZADA", "Comprueba el código de preparación configurado en el servidor.", 403);
        }
    }

    @Transactional
    public Challenge initialFingerprint(InitialChallenge request, String ip) {
        limitAttempts();
        checkSetupCode(request.codigo());
        people.configuration(true);
        require(access.administrator() == null, "ADMINISTRADOR_EXISTE", "El administrador ya está configurado.");
        return attendance.challenge(new ChallengeRequest("ALTA_HUELLA", request.dni(), request.clave(), null), ip);
    }

    @Transactional
    public void prepareAdministrator(InitialAdministrator request, String ip) {
        limitAttempts();
        checkSetupCode(request.codigo());
        validateDni(request.dni());
        validateKey(request.claveDispositivo());
        people.configuration(true);
        Administrator current = access.administrator();
        if (current != null) {
            require(current.dni().equals(request.dni()), "ADMINISTRADOR_EXISTE", "El administrador existente debe conservar su DNI.");
            if (request.registro() != null) {
                require(current.publicKey().equals(request.claveDispositivo()), "ADMINISTRADOR_EXISTE", "Autoriza el dispositivo con la ficha existente.");
                return;
            }
        }
        Person person;
        if (request.registro() == null) {
            person = activePerson(request.dni());
        } else {
            require(request.dni().equals(request.registro().dni()), "DNI_INVALIDO", "Comprueba el DNI del registro.");
            require(!"GALERIA".equals(request.registro().origenRostro()),
                    "CAPTURA_REQUERIDA", "Captura el rostro del administrador con la cámara.");
            person = attendance.register(request.registro(), ip);
        }
        require(person.rostro() || person.huella(), "BIOMETRIA_REQUERIDA", "Registra el rostro o vincula la huella del administrador.");
        Credentials credentials = request.credenciales();
        String username = current == null ? null : current.username();
        String passwordHash = current == null ? null : current.passwordHash();
        if (credentials != null) {
            username = normalizeUsername(credentials.usuario());
            require(username != null, "USUARIO_INVALIDO", "Usa de 3 a 40 letras, números, puntos, guiones o guiones bajos.");
            String password = credentials.contrasena();
            require(password != null && password.length() >= 15 && password.length() <= 128 && !password.isBlank(),
                    "CONTRASENA_INVALIDA", "Usa una contraseña de 15 a 128 caracteres.");
            passwordHash = PasswordHash.create(password, random);
        }
        require(username != null && passwordHash != null, "CREDENCIALES_REQUERIDAS",
                "Define un usuario y una contraseña para el administrador.");
        Administrator account = new Administrator(person.dni(), request.claveDispositivo(), username, passwordHash);
        if (current == null) {
            access.createAdministrator(account);
        } else {
            access.updateAdministrator(account);
            access.revokeSessions(person.dni());
        }
    }

    @Transactional
    public Challenge challenge(LoginRequest request, String ip) {
        limitAttempts();
        require(request.metodo() != null, "ACCESO_INVALIDO", "Elige un método de acceso.");
        Administrator account = access.administrator();
        if (account == null) throw denied();
        Person person = activePerson(account.dni());
        String key;
        if (request.metodo() == Method.HUELLA) {
            validateKey(request.clave());
            Device device = people.device(Rules.deviceId(request.clave()));
            require(device != null && device.dni().equals(person.dni()) && device.clave().equals(request.clave()),
                    "DISPOSITIVO_SIN_VINCULAR", "Usa el teléfono vinculado al administrador o elige otro método.");
            key = device.clave();
        } else {
            require(person.rostro(), "ROSTRO_NO_REGISTRADO", "Esta ficha requiere un rostro registrado para usar la cámara.");
            require(account.publicKey().equals(request.clave()), "DISPOSITIVO_NO_AUTORIZADO",
                    "Autoriza este dispositivo para el acceso facial del administrador.");
            key = account.publicKey();
        }
        String purpose = ADMIN_LOGIN_PREFIX + request.metodo().name();
        Challenge challenge = new Challenge(UUID.randomUUID().toString(), randomToken(), purpose,
                person.dni(), key, clock.instant(), null, ip, 0);
        people.insertChallenge(challenge);
        return challenge;
    }

    @Transactional(noRollbackFor = RuleViolation.class)
    public Session login(Proof proof, String ip) {
        limitAttempts();
        var configuration = people.configuration(true);
        Challenge challenge = people.loginChallenge(proof.reto());
        Instant now = clock.instant();
        if (challenge == null || !challenge.proposito().startsWith(ADMIN_LOGIN_PREFIX) ||
                !challenge.ip().equals(ip) || now.isBefore(challenge.creado()) ||
                !now.isBefore(challenge.creado().plus(CHALLENGE_DURATION))) throw denied();
        people.consume(challenge.id());
        Person person = activePerson(challenge.dni());
        Administrator account = access.administrator();
        if (account == null || !account.dni().equals(person.dni())) throw denied();
        if (challenge.proposito().endsWith("_HUELLA")) {
            Device device = people.device(Rules.deviceId(challenge.clave()));
            if (device == null || !device.dni().equals(person.dni()) || !device.clave().equals(challenge.clave())) throw denied();
            Rules.signature(challenge, proof.firma());
        } else {
            if (!account.publicKey().equals(challenge.clave())) throw denied();
            Rules.signature(challenge, proof.firma());
            if (!MODEL.equals(proof.modelo()) || !person.rostro()) throw denied();
            Match match = Rules.identify(proof.vector(), people.templates(), configuration.umbral());
            if (!person.dni().equals(match.dni())) throw denied();
        }
        return issueSession(person.dni(), Role.ADMINISTRADOR);
    }

    @Transactional
    public Session loginWithCredentials(Credentials credentials) {
        limitAttempts();
        String username = normalizeUsername(credentials.usuario());
        String password = credentials.contrasena();
        if (username == null || password == null || password.isEmpty() || password.length() > 128) throw invalidCredentials();
        people.configuration(true);
        Administrator account = access.administrator();
        boolean valid = PasswordHash.matches(password, account == null ? null : account.passwordHash());
        if (!valid || account == null || !username.equals(account.username())) throw invalidCredentials();
        return issueSession(activePerson(account.dni()).dni(), Role.ADMINISTRADOR);
    }

    @Transactional
    public AttendanceReceipt markReceipt(Proof proof, String ip) {
        AttendanceMark mark = attendance.markForReceipt(proof, ip);
        Session session = issueSession(mark.persona().dni(), Role.PERSONA);
        return new AttendanceReceipt(mark.asistencia(), mark.persona(), mark.yaMarcada(), session);
    }

    private Session issueSession(String dni, Role role) {
        String token = randomToken();
        Instant now = clock.instant();
        Instant expires = now.plus(Duration.ofMinutes(role == Role.ADMINISTRADOR ? 15 : 5));
        access.createSession(hash(token), new Principal(dni, role), expires, now);
        return new Session(token, dni, role, expires);
    }

    private static String normalizeUsername(String input) {
        if (input == null || input.length() > 80) return null;
        String name = input.trim().toLowerCase(Locale.ROOT);
        return name.matches("[a-z0-9._-]{3,40}") ? name : null;
    }

    private static RuleViolation invalidCredentials() {
        return new RuleViolation("CREDENCIALES_INVALIDAS", "Revisa el usuario y la contraseña.", 401);
    }

    public Principal requireSession(String authorization) {
        String tokenHash = bearerHash(authorization);
        Principal principal = tokenHash == null ? null : access.session(tokenHash, clock.instant());
        if (principal == null) throw new RuleViolation("SESION_REQUERIDA", "Inicia sesión para continuar.", 401);
        return principal;
    }

    public void logout(String authorization) {
        String tokenHash = bearerHash(authorization);
        if (tokenHash != null) access.deleteSession(tokenHash);
    }

    public Person profile(Principal principal) { return activePerson(principal.dni()); }

    public Person updateProfile(Principal principal, ProfileUpdate update, String ip) {
        return attendance.update(principal.dni(),
                new PersonUpdate(update.nombre(), update.foto(), null, null, null, false, update.version()), ip);
    }

    private Person activePerson(String dni) {
        Person person = people.person(dni, false);
        if (person == null) throw denied();
        return person;
    }

    private void validateDni(String dni) {
        require(dni != null && dni.matches("[0-9]{8}"), "DNI_INVALIDO", "El DNI debe tener ocho dígitos.");
    }

    private void validateKey(String key) {
        require(key != null && key.length() <= 512, "CLAVE_INVALIDA", "No se pudo validar este dispositivo.");
        Rules.deviceId(key);
    }

    private synchronized void limitAttempts() {
        Instant now = clock.instant();
        if (now.isBefore(attemptWindow) || !now.isBefore(attemptWindow.plusSeconds(60))) {
            attemptWindow = now;
            attempts = 0;
        }
        if (attempts >= 30) throw new RuleViolation("INTENTOS_ACCESO", "Espera un minuto antes de volver a intentar.", 429);
        attempts++;
    }

    private RuleViolation denied() {
        return new RuleViolation("ACCESO_DENEGADO", "No se pudo verificar el acceso.", 403);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String bearerHash(String header) {
        if (header == null || !header.matches("Bearer [A-Za-z0-9_-]{43}")) return null;
        return hash(header.substring(7));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
