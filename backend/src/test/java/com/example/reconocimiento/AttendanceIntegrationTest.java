package com.example.reconocimiento;

import com.example.reconocimiento.application.AttendanceService;
import com.example.reconocimiento.application.VerificationService;
import com.example.reconocimiento.domain.*;
import static com.example.reconocimiento.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:attendance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@Import(AttendanceIntegrationTest.TimeConfiguration.class)
class AttendanceIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        if(Boolean.getBoolean("reconocimiento.test.postgres")){
            var env=Dotenv.configure().ignoreIfMissing().load();
            registry.add("spring.datasource.url",()->"jdbc:postgresql://localhost:5432/reconocimiento_verificacion");
            registry.add("spring.datasource.username",()->env.get("DB_USER","postgres"));
            registry.add("spring.datasource.password",()->env.get("DB_PASSWORD"));
        }
    }
    @Autowired AttendanceService service;
    @Autowired VerificationService verification;
    @Autowired JdbcTemplate db;
    @Autowired MutableClock clock;
    @Autowired WebApplicationContext web;
    @TestConfiguration static class TimeConfiguration { @Bean @Primary MutableClock testClock(){return new MutableClock();} }
    static class MutableClock extends Clock {
        Instant value=Instant.parse("2026-11-01T04:59:50Z");
        boolean advanceOnZone=false;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){Instant observed=value;if(advanceOnZone)value=value.plusMillis(20);return Clock.fixed(observed,zone);}
        public Instant instant(){return value;}
    }
    static final String IP="127.0.0.1";
    @BeforeEach void reset(){
        clock.value=Instant.parse("2026-11-01T04:59:50Z");
        clock.advanceOnZone=false;
        for(String table:List.of("ensayo_facial","asistencia","reto","dispositivo","rostro","persona"))db.update("DELETE FROM reconocimiento."+table);
        service.configure(new Configuration("Sede",0d,0d,100,"",.8,0));
        service.register(new Enrollment("12345678","Ana Pérez","",MODEL,List.of(RulesTest.face(0),RulesTest.face(0),RulesTest.face(0))));
    }
    Challenge faceChallenge(){return service.challenge(new ChallengeRequest("FACIAL",null,null,new Position(0,0,5,0,false)),IP);}
    Proof faceProof(Challenge c){return new Proof(c.id(),null,MODEL,RulesTest.face(0));}
    @Test void facialAttendanceIsUniqueAndChallengeCannotBeReused(){
        Challenge c=faceChallenge();assertEquals("12345678",service.mark(faceProof(c),IP).dni());
        assertThrows(RuleViolation.class,()->service.mark(faceProof(c),IP));
        Challenge next=faceChallenge();assertEquals("YA_MARCO",assertThrows(RuleViolation.class,()->service.mark(faceProof(next),IP)).code);
        assertEquals(1,service.attendance(null).size());
    }
    @Test void deletedPersonCannotMarkEvenWithAnEarlierChallenge(){
        Challenge c=faceChallenge();service.remove("12345678");assertThrows(RuleViolation.class,()->service.mark(faceProof(c),IP));assertTrue(service.people().isEmpty());assertTrue(service.attendance(null).isEmpty());
    }
    @Test void configurationChangesInvalidatePendingAttendance(){
        Challenge c=faceChallenge();service.configure(new Configuration("Nueva sede",1d,1d,100,"",.8,0));
        assertEquals("CONFIG_CAMBIO",assertThrows(RuleViolation.class,()->service.mark(faceProof(c),IP)).code);
    }
    @Test void changedConnectionAndUnknownFaceAreRejected(){
        Challenge c=faceChallenge();assertThrows(RuleViolation.class,()->service.mark(faceProof(c),"192.168.1.2"));
        assertThrows(RuleViolation.class,()->service.mark(new Proof(c.id(),null,MODEL,RulesTest.face(1)),IP));assertTrue(service.attendance(null).isEmpty());
    }
    @Test void expiredProofsAndChangedEnrollmentCannotBeUsed(){
        Challenge c=faceChallenge();clock.value=clock.value.plusSeconds(46);
        assertEquals("RETO_VENCIDO",assertThrows(RuleViolation.class,()->service.mark(faceProof(c),IP)).code);
        assertTrue(service.attendance(null).isEmpty());
    }
    @Test void theDailyLimitUsesTheDateInLimaRatherThanUtc(){
        service.mark(faceProof(faceChallenge()),IP);
        clock.value=clock.value.plusSeconds(12);
        assertDoesNotThrow(()->service.mark(faceProof(faceChallenge()),IP));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.asistencia",Integer.class));
    }
    @Test void fingerprintSignatureUsesBoundIdentityAndSharesTheDailyLimit()throws Exception{
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(256);KeyPair keys=g.generateKeyPair();String pub=Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        Challenge bind=service.challenge(new ChallengeRequest("VINCULAR","12345678",pub,null),IP);
        service.bind(new Proof(bind.id(),sign(bind,keys),MODEL,RulesTest.face(0)),IP);
        Challenge finger=service.challenge(new ChallengeRequest("HUELLA",null,pub,new Position(0,0,5,0,false)),IP);
        assertEquals("HUELLA",service.mark(new Proof(finger.id(),sign(finger,keys),null,null),IP).metodo());
        assertThrows(RuleViolation.class,()->service.mark(faceProof(faceChallenge()),IP));
        service.remove("12345678");assertThrows(RuleViolation.class,()->service.device(pub));
    }
    @Test void duplicateFingerprintIsCheckedOnlyAfterSuccessfulAuthentication()throws Exception{
        KeyPair keys=keys();bindExisting(keys);service.mark(faceProof(faceChallenge()),IP);
        Challenge challenge=service.challenge(new ChallengeRequest("HUELLA",null,publicKey(keys),new Position(0,0,5,0,false)),IP);
        assertEquals("FIRMA_INVALIDA",assertThrows(RuleViolation.class,()->service.mark(new Proof(challenge.id(),"invalid",null,null),IP)).code);
        assertEquals("YA_MARCO",assertThrows(RuleViolation.class,()->service.mark(new Proof(challenge.id(),sign(challenge,keys),null,null),IP)).code);
        assertEquals(1,service.attendance(null).size());
    }
    @Test void fingerprintOnlyPersonCanEnrollAndAttendWithoutAFace()throws Exception{
        KeyPair keys=keys();String pub=publicKey(keys);
        Challenge pending=service.challenge(new ChallengeRequest("ALTA_HUELLA","87654321",pub,null),IP);
        Person p=service.register(new Enrollment("87654321","Ingrid López","",null,null,new Proof(pending.id(),sign(pending,keys),null,null)),IP);
        assertTrue(p.huella());assertFalse(p.rostro());
        Challenge c=service.challenge(new ChallengeRequest("HUELLA",null,pub,new Position(0,0,5,0,false)),IP);
        Attendance a=service.mark(new Proof(c.id(),sign(c,keys),null,null),IP);
        assertEquals(p.dni(),a.dni());assertNull(a.similitud());assertEquals("",a.foto());assertNull(a.umbral());
    }
    @Test void incompletePersonCanAddFingerprintLaterAndCannotMatchAnotherFace()throws Exception{
        Person p=service.register(new Enrollment("87654321","Ingrid López","",null,null));
        assertFalse(p.rostro());assertFalse(p.huella());
        assertEquals("PERSONA_NO_RECONOCIDA",assertThrows(RuleViolation.class,()->service.mark(new Proof(faceChallenge().id(),null,MODEL,RulesTest.face(1)),IP)).code);
        KeyPair keys=keys();Challenge c=service.challenge(new ChallengeRequest("EDITAR_HUELLA",p.dni(),publicKey(keys),null),IP);
        Person updated=service.update(p.dni(),new PersonUpdate(p.nombre(),null,null,null,new Proof(c.id(),sign(c,keys),null,null),false,p.version()),IP);
        assertTrue(updated.huella());assertFalse(updated.rostro());
    }
    @Test void facialEvidenceAndThresholdAreStoredAsHistoricalSnapshots()throws Exception{
        Person p=service.people().getFirst();String photo=samplePhoto();
        service.update(p.dni(),new PersonUpdate(p.nombre(),photo,null,null,null,false,p.version()),IP);
        Challenge c=faceChallenge();Attendance saved=service.mark(new Proof(c.id(),null,MODEL,RulesTest.face(0),photo),IP);
        service.remove(p.dni());service.configure(new Configuration("Otra sede",0d,0d,100,"",.9,0));
        Attendance history=service.attendance(null).getFirst();
        assertEquals(saved.id(),history.id());assertEquals(photo,history.foto());assertEquals(photo,history.fotoRegistro());
        assertEquals(.8,history.umbral());assertEquals(1.0,history.similitud(),.000001);
    }
    @Test void concurrentRequestsCannotInsertTwoAttendances()throws Exception{
        Challenge a=faceChallenge(),b=faceChallenge();
        try(ExecutorService pool=Executors.newFixedThreadPool(2)){
            List<Future<Boolean>> results=pool.invokeAll(List.of(()->attempt(a),()->attempt(b)));
            int successes=0;for(Future<Boolean> r:results)if(r.get())successes++;
            assertEquals(1,successes);assertEquals(1,service.attendance(null).size());
        }
    }
    @Test void nameAndPhotoCanChangeWithoutReplacingBiometrics()throws Exception{
        KeyPair keys=keys();bindExisting(keys);
        Person person=service.people().getFirst();String photo=samplePhoto();
        Person saved=service.update(person.dni(),new PersonUpdate("Ana María Pérez",photo,null,null,null,false,person.version()),IP);
        assertEquals("Ana María Pérez",saved.nombre());assertEquals(photo,saved.foto());assertTrue(saved.rostro());assertTrue(saved.huella());
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro",Integer.class));
    }
    @Test void databaseChangesAreReadAndStaleEditorsCannotOverwriteThem(){
        Person before=service.people().getFirst();
        db.update("UPDATE reconocimiento.persona SET nombre=? WHERE dni=?","Ana del Pilar Pérez",before.dni());
        Person fresh=service.people().getFirst();assertEquals("Ana del Pilar Pérez",fresh.nombre());assertNotEquals(before.version(),fresh.version());
        assertEquals("PERSONA_CONFLICTO",assertThrows(RuleViolation.class,()->service.update(before.dni(),new PersonUpdate("Nombre anterior",null,null,null,null,false,before.version()),IP)).code);
    }
    @Test void pendingFingerprintAndPersonAreSavedTogether()throws Exception{
        KeyPair keys=keys();String pub=publicKey(keys);
        Challenge pending=service.challenge(new ChallengeRequest("ALTA_HUELLA","87654321",pub,null),IP);
        Person saved=service.register(new Enrollment("87654321","Ingrid López","",MODEL,
            List.of(RulesTest.face(1),RulesTest.face(1),RulesTest.face(1)),new Proof(pending.id(),sign(pending,keys),null,null)),IP);
        assertTrue(saved.huella());assertEquals(saved.dni(),service.device(pub).dni());
    }
    @Test void failedFingerprintDoesNotLeaveAnIncompletePerson()throws Exception{
        KeyPair keys=keys();Challenge pending=service.challenge(new ChallengeRequest("ALTA_HUELLA","87654321",publicKey(keys),null),IP);
        assertThrows(RuleViolation.class,()->service.register(new Enrollment("87654321","Ingrid López","",MODEL,
            List.of(RulesTest.face(1),RulesTest.face(1),RulesTest.face(1)),new Proof(pending.id(),"invalid",null,null)),IP));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.persona WHERE dni='87654321'",Integer.class));
    }
    @Test void fingerprintCanBeReplacedAndRemovedFromTheEditor()throws Exception{
        KeyPair oldKeys=keys();bindExisting(oldKeys);KeyPair nextKeys=keys();Person person=service.people().getFirst();
        Challenge pending=service.challenge(new ChallengeRequest("EDITAR_HUELLA",person.dni(),publicKey(nextKeys),null),IP);
        Person saved=service.update(person.dni(),new PersonUpdate(person.nombre(),null,null,null,
            new Proof(pending.id(),sign(pending,nextKeys),MODEL,RulesTest.face(0)),false,person.version()),IP);
        assertTrue(saved.huella());assertThrows(RuleViolation.class,()->service.device(publicKey(oldKeys)));
        assertEquals(person.dni(),service.device(publicKey(nextKeys)).dni());
        Person removed=service.update(saved.dni(),new PersonUpdate(saved.nombre(),null,null,null,null,true,saved.version()),IP);
        assertFalse(removed.huella());assertThrows(RuleViolation.class,()->service.device(publicKey(nextKeys)));
    }
    @Test void replacingFaceRevokesTheOldFingerprint()throws Exception{
        KeyPair keys=keys();bindExisting(keys);Person person=service.people().getFirst();
        Person updated=service.update(person.dni(),new PersonUpdate(person.nombre(),null,MODEL,
            List.of(RulesTest.face(2),RulesTest.face(2),RulesTest.face(2)),null,false,person.version()),IP);
        assertTrue(updated.rostro());assertFalse(updated.huella());
        assertThrows(RuleViolation.class,()->service.mark(faceProof(faceChallenge()),IP));
        assertThrows(RuleViolation.class,()->service.device(publicKey(keys)));
    }
    @Test void linkedFingerprintCanBeRenewedWithoutOpeningTheCameraButNewKeyNeedsFace()throws Exception{
        KeyPair keys=keys();bindExisting(keys);Person person=service.people().getFirst();
        Challenge renewal=service.challenge(new ChallengeRequest("EDITAR_HUELLA",person.dni(),publicKey(keys),null),IP);
        assertFalse(service.requiresFace(renewal));
        assertThrows(RuleViolation.class,()->service.update(person.dni(),new PersonUpdate(person.nombre(),null,null,null,new Proof(renewal.id(),"invalid",null,null),false,person.version()),IP));
        Person renewed=service.update(person.dni(),new PersonUpdate(person.nombre(),null,null,null,new Proof(renewal.id(),sign(renewal,keys),null,null),false,person.version()),IP);
        assertTrue(renewed.huella());
        KeyPair newKeys=keys();Challenge replacement=service.challenge(new ChallengeRequest("EDITAR_HUELLA",person.dni(),publicKey(newKeys),null),IP);
        assertTrue(service.requiresFace(replacement));
        assertThrows(RuleViolation.class,()->service.update(renewed.dni(),new PersonUpdate(renewed.nombre(),null,null,null,new Proof(replacement.id(),sign(replacement,newKeys),null,null),false,renewed.version()),IP));
        assertEquals(person.dni(),service.device(publicKey(keys)).dni());
    }
    @Test void reportIncludesPreviousDaysAndDeletedPeopleWithoutBiometricPhotos()throws Exception{
        service.mark(faceProof(faceChallenge()),IP);
        clock.value=Instant.parse("2026-11-01T14:20:00Z");
        service.mark(faceProof(faceChallenge()),IP);
        service.remove("12345678");
        var mvc=MockMvcBuilders.webAppContextSetup(web).build();
        mvc.perform(get("/api/asistencias/reporte")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].fecha").value("2026-11-01")).andExpect(jsonPath("$[0].nombre").value("Ana Pérez"))
            .andExpect(jsonPath("$[0].foto").value("")).andExpect(jsonPath("$[0].fotoRegistro").value(""));
        mvc.perform(get("/api/asistencias/reporte").param("desde","2026-10-31").param("hasta","2026-10-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fecha").value("2026-10-31"));
        byte[] bytes=mvc.perform(get("/api/asistencias/reporte.xlsx")).andExpect(status().isOk())
            .andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){
            assertEquals(2,book.getSheetAt(0).getLastRowNum());assertEquals("12345678",book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.asistencia",Integer.class));
    }
    @Test void reportRejectsIncompleteReversedOrFuturePeriods()throws Exception{
        var mvc=MockMvcBuilders.webAppContextSetup(web).build();
        for(String[] period:List.of(new String[]{"2026-10-31",""},new String[]{"2026-10-31","2026-10-30"},new String[]{"2026-10-31","2026-11-01"})){
            var request=get("/api/asistencias/reporte.xlsx").param("desde",period[0]);
            if(!period[1].isEmpty())request.param("hasta",period[1]);
            mvc.perform(request).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
        }
        mvc.perform(get("/api/asistencias/reporte").param("desde","incorrecta")).andExpect(status().isBadRequest());
    }
    @Test void configurationReadsDirectDatabaseChangesAndDetectsConflicts(){
        Configuration before=service.configuration();db.update("UPDATE reconocimiento.configuracion SET radio=175 WHERE id=1");
        assertEquals(175,service.configuration().radio());
        assertEquals("CONFIG_CONFLICTO",assertThrows(RuleViolation.class,()->service.configure(new Configuration(
            before.nombre(),before.latitud(),before.longitud(),100,before.red(),before.umbral(),before.revision(),before.version()))).code);
    }
    @Test void invalidProfileDataIsRejectedWithoutChangingThePerson(){
        Person p=service.people().getFirst();
        assertThrows(RuleViolation.class,()->service.update(p.dni(),new PersonUpdate("Ana 123",null,null,null,null,false,p.version()),IP));
        assertThrows(RuleViolation.class,()->service.update(p.dni(),new PersonUpdate(p.nombre(),"not-an-image",null,null,null,false,p.version()),IP));
        assertEquals("CAPTURAS_INSUFICIENTES",assertThrows(RuleViolation.class,()->service.replace(p.dni(),new Enrollment(p.dni(),p.nombre(),"",null,null))).code);
        assertEquals(p.version(),service.people().getFirst().version());
    }
    @Test void calibrationClassifiesErrorsWithoutCreatingAttendance(){
        assertEquals("COINCIDENCIA_CORRECTA",verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678")).prueba().resultado());
        assertEquals("RECHAZO_CORRECTO",verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(1),null)).prueba().resultado());
        assertEquals("FALSO_RECHAZO",verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(1),"12345678")).prueba().resultado());
        assertEquals("FALSA_ACEPTACION",verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),null)).prueba().resultado());
        service.register(new Enrollment("87654321","Ingrid López","",MODEL,List.of(RulesTest.face(1),RulesTest.face(1),RulesTest.face(1))));
        FaceTestResult wrong=verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(1),"12345678"));
        assertEquals("IDENTIDAD_INCORRECTA",wrong.prueba().resultado());assertEquals(1,wrong.resumen().total());
        assertEquals(0,wrong.prueba().similitudEsperada(),.00001);assertEquals(1,wrong.prueba().similitud(),.00001);
        assertEquals(5,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.ensayo_facial",Integer.class));
        assertTrue(service.attendance(null).isEmpty());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.reto",Integer.class));
    }
    @Test void calibrationSummaryKeepsThresholdsAndRegistriesSeparate(){
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678"));
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(1),"12345678"));
        TrialSummary summary=verification.summary();assertEquals(2,summary.total());assertEquals(1,summary.correctos());assertEquals(1,summary.falsosRechazos());
        service.configure(new Configuration("Sede",0d,0d,100,"",.9,0));assertEquals(0,verification.summary().total());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.ensayo_facial",Integer.class));
        service.remove("12345678");assertThrows(RuleViolation.class,()->verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678")));
    }
    @Test void calibrationDoesNotMixTheFastAndStableCapturePolicies(){
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678","legacy-v1"));
        assertEquals(0,verification.summary().total());assertEquals(1,verification.summary("legacy-v1").total());
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678",CAPTURE_POLICY));
        assertEquals(1,verification.summary().total());
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678","frontal-estable-v2"));
        assertEquals(1,verification.summary("frontal-estable-v2").total());assertEquals(1,verification.summary().total());
        verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678","frontal-accesorios-v3"));
        assertEquals(1,verification.summary("frontal-accesorios-v3").total());assertEquals(1,verification.summary().total());
        for(String policy:List.of("guiada-manual-v4","guiada-automatica-v5")){
            verification.faceTest(new FaceTestRequest(MODEL,RulesTest.face(0),"12345678",policy));
            assertEquals(1,verification.summary(policy).total());assertEquals(1,verification.summary().total());
        }
        assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.ensayo_facial",Integer.class));
    }
    @Test void facialReferenceIsPreservedWhenTheProfilePhotoChanges()throws Exception{
        String reference=samplePhoto();Person p=service.register(new Enrollment("87654321","Ingrid López",reference,MODEL,List.of(RulesTest.face(1),RulesTest.face(1),RulesTest.face(1)),null,reference));
        service.update(p.dni(),new PersonUpdate(p.nombre(),"",null,null,null,false,p.version()),IP);
        Attendance marked=service.mark(new Proof(faceChallenge().id(),null,MODEL,RulesTest.face(1),reference),IP);
        assertEquals("ROSTRO",marked.tipoFotoRegistro());assertEquals(reference,marked.fotoRegistro());
        service.remove(p.dni());assertEquals(reference,service.attendance(null).getFirst().fotoRegistro());
    }
    @Test void locationCheckUsesAttendanceRulesAndDoesNotWriteAttendance(){
        assertTrue(verification.zoneCheck(new Position(0,0,5,0,false),IP).permitida());
        ZoneCheck outside=verification.zoneCheck(new Position(Math.toDegrees(101d/6371000),0,5,0,false),IP);
        assertFalse(outside.permitida());assertEquals("FUERA_DE_ZONA",outside.codigo());assertEquals(101,outside.distancia(),.01);
        assertEquals("UBICACION_IMPRECISA",verification.zoneCheck(new Position(0,0,26,0,false),IP).codigo());
        assertEquals("UBICACION_SIMULADA",verification.zoneCheck(new Position(0,0,5,0,true),IP).codigo());
        service.configure(new Configuration("Sede",0d,0d,100,"192.168.1.0/24",.8,0));
        ZoneCheck wrongNetwork=verification.zoneCheck(new Position(0,0,5,0,false),IP);
        assertFalse(wrongNetwork.redPermitida());assertEquals("IP_NO_PERMITIDA",wrongNetwork.codigo());
        assertTrue(service.attendance(null).isEmpty());
    }
    @Test void historicalAttendanceIsQueriedByLimaDateThroughHttpAndSurvivesDeletion()throws Exception{
        service.mark(faceProof(faceChallenge()),IP);clock.value=clock.value.plusSeconds(12);
        service.mark(faceProof(faceChallenge()),IP);service.remove("12345678");
        var http=MockMvcBuilders.webAppContextSetup(web).build();
        http.perform(get("/api/asistencias").param("fecha","2026-10-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fecha").value("2026-10-31")).andExpect(jsonPath("$[0].nombre").value("Ana Pérez"));
        http.perform(get("/api/asistencias").param("fecha","2026-11-01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fecha").value("2026-11-01"));
        http.perform(get("/api/asistencias")).andExpect(status().isOk()).andExpect(jsonPath("$[0].fecha").value("2026-11-01"));
        http.perform(get("/api/asistencias").param("fecha","2026-10-30")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.perform(get("/api/asistencias").param("fecha","fecha-invalida")).andExpect(status().isBadRequest());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.asistencia",Integer.class));
    }
    @Test void attendanceDateAndStoredTimeUseTheSameInstantAtMidnight(){
        Challenge challenge=faceChallenge();clock.value=Instant.parse("2026-11-01T04:59:59.999999999Z");clock.advanceOnZone=true;
        Attendance marked=service.mark(faceProof(challenge),IP);clock.advanceOnZone=false;
        assertEquals(marked.instante().atZone(ZoneId.of("America/Lima")).toLocalDate(),marked.fecha());
        assertEquals(0,marked.instante().getNano()%1000);
        assertEquals(marked.instante(),service.attendance(marked.fecha()).getFirst().instante());
    }
    @Test void personCrudRoundTripPersistsBiometricsAndReadsSqlChangesThroughHttp()throws Exception{
        var http=MockMvcBuilders.webAppContextSetup(web).build();KeyPair key=keys();String photo=samplePhoto();
        Challenge pending=service.challenge(new ChallengeRequest("ALTA_HUELLA","87654321",publicKey(key),null),IP);
        String vector=Arrays.toString(RulesTest.face(1));
        String create="""
            {"dni":"87654321","nombre":"Ingrid López","foto":"","modelo":"%s","vectores":[%s,%s,%s],"fotoRostro":"%s","huella":{"reto":"%s","firma":"%s"}}
            """.formatted(MODEL,vector,vector,vector,photo,pending.id(),sign(pending,key));
        http.perform(post("/api/personas").contentType("application/json").content(create))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.rostro").value(true)).andExpect(jsonPath("$.huella").value(true));
        Person p=service.people().stream().filter(x->x.dni().equals("87654321")).findFirst().orElseThrow();
        assertEquals(photo,db.queryForObject("SELECT foto_rostro FROM reconocimiento.persona WHERE dni=?",String.class,p.dni()));
        String update="""
            {"nombre":"Ingrid María López","foto":"%s","version":"%s"}
            """.formatted(photo,p.version());
        http.perform(put("/api/personas/87654321").contentType("application/json").content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Ingrid María López")).andExpect(jsonPath("$.foto").value(photo)).andExpect(jsonPath("$.huella").value(true));
        db.update("UPDATE reconocimiento.persona SET nombre='Ingrid del Pilar López' WHERE dni='87654321'");
        http.perform(get("/api/personas")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.dni=='87654321')].nombre").value(org.hamcrest.Matchers.contains("Ingrid del Pilar López")));
        http.perform(put("/api/personas/87654321").contentType("application/json").content(update))
            .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("PERSONA_CONFLICTO"));
        Challenge old=service.challenge(new ChallengeRequest("HUELLA",null,publicKey(key),new Position(0,0,5,0,false)),IP);
        http.perform(delete("/api/personas/87654321")).andExpect(status().isOk());
        assertFalse(db.queryForObject("SELECT activo FROM reconocimiento.persona WHERE dni='87654321'",Boolean.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro WHERE dni='87654321'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.dispositivo WHERE dni='87654321'",Integer.class));
        assertEquals("",db.queryForObject("SELECT foto_rostro FROM reconocimiento.persona WHERE dni='87654321'",String.class));
        assertThrows(RuleViolation.class,()->service.mark(new Proof(old.id(),sign(old,key),null,null),IP));
        http.perform(get("/api/personas")).andExpect(jsonPath("$[?(@.dni=='87654321')]").isEmpty());
    }
    KeyPair keys()throws Exception{KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(256);return generator.generateKeyPair();}
    @Test void dniAvailabilityChecksActiveAndDeletedRecordsWithoutCreatingAnything()throws Exception{
        var http=MockMvcBuilders.webAppContextSetup(web).build();
        http.perform(get("/api/personas/87654321/disponibilidad")).andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
        http.perform(get("/api/personas/12345678/disponibilidad")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("DNI_DUPLICADO"));
        service.remove("12345678");
        http.perform(get("/api/personas/12345678/disponibilidad")).andExpect(status().isUnprocessableEntity());
        http.perform(get("/api/personas/abc/disponibilidad")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("DNI_INVALIDO"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.persona",Integer.class));
    }
    @Test void importedPhotoCreatesOneTemplateRecognizesAndCanBeReplaced()throws Exception{
        String photo=samplePhoto();
        Enrollment e=new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(RulesTest.face(1)),null,photo,"GALERIA",true);
        service.validateEnrollment(new EnrollmentValidation(e,null));
        assertEquals(1,service.people().size());assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro",Integer.class));
        Person p=service.register(e);assertTrue(p.rostro());assertEquals("GALERIA",p.origenRostro());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro WHERE dni='87654321'",Integer.class));
        assertEquals("GALERIA",db.queryForObject("SELECT origen_rostro FROM reconocimiento.persona WHERE dni='87654321'",String.class));
        Attendance marked=service.mark(new Proof(faceChallenge().id(),null,MODEL,RulesTest.face(1),photo),IP);
        assertEquals(p.dni(),marked.dni());assertEquals(photo,marked.fotoRegistro());assertEquals("GALERIA",marked.tipoFotoRegistro());
        Enrollment replacement=new Enrollment(p.dni(),p.nombre(),photo,MODEL,List.of(RulesTest.face(2)),null,photo,"GALERIA",true);
        service.validateEnrollment(new EnrollmentValidation(replacement,p.version()));
        Person edited=service.update(p.dni(),new PersonUpdate(p.nombre(),photo,MODEL,replacement.vectores(),null,false,p.version(),photo,"GALERIA",true),IP);
        assertEquals("GALERIA",edited.origenRostro());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro WHERE dni='87654321'",Integer.class));
        service.remove(p.dni());assertThrows(RuleViolation.class,()->service.mark(new Proof(faceChallenge().id(),null,MODEL,RulesTest.face(2),photo),IP));
        assertEquals(1,service.attendance(null).size());
    }
    @Test void importRequiresVisualReviewAndRejectsDuplicateFacesBeforeAndDuringSave()throws Exception{
        String photo=samplePhoto();
        Enrollment duplicate=new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(RulesTest.face(0)),null,photo,"GALERIA",true);
        assertEquals("ROSTRO_DUPLICADO",assertThrows(RuleViolation.class,()->service.validateEnrollment(new EnrollmentValidation(duplicate,null))).code);
        assertEquals("ROSTRO_DUPLICADO",assertThrows(RuleViolation.class,()->service.register(duplicate)).code);
        double[] borderline=RulesTest.face(1);borderline[0]=.82;borderline[1]=Math.sqrt(1-.82*.82);
        assertEquals("ROSTRO_DUPLICADO",assertThrows(RuleViolation.class,()->service.register(new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(borderline),null,photo,"GALERIA",true))).code);
        assertEquals("REVISION_REQUERIDA",assertThrows(RuleViolation.class,()->service.register(new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(RulesTest.face(1)),null,photo,"GALERIA",false))).code);
        assertEquals("CAPTURAS_INSUFICIENTES",assertThrows(RuleViolation.class,()->service.register(new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(RulesTest.face(1)),null,photo,"CAMARA",true))).code);
        assertEquals("ORIGEN_INVALIDO",assertThrows(RuleViolation.class,()->service.register(new Enrollment("87654321","Ingrid López",photo,MODEL,List.of(RulesTest.face(1)),null,photo,"OTRO",true))).code);
        var http=MockMvcBuilders.webAppContextSetup(web).build();String vector=Arrays.toString(RulesTest.face(1));
        String preview="""
            {"registro":{"dni":"87654321","nombre":"Ingrid López","foto":"%s","fotoRostro":"%s","modelo":"%s","vectores":[%s],"origenRostro":"GALERIA","revisionVisual":true}}
            """.formatted(photo,photo,MODEL,vector);
        http.perform(post("/api/personas/validar").contentType("application/json").content(preview)).andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(true));
        assertEquals(1,service.people().size());assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro",Integer.class));
        http.perform(post("/api/personas").contentType("application/json").content(preview.substring(preview.indexOf(":")+1,preview.lastIndexOf("}"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.rostro").value(true)).andExpect(jsonPath("$.origenRostro").value("GALERIA"));
    }
    @Test void editingFacePreviewUsesTheEnrollmentSchemaAndCanSaveTheSameIdentity()throws Exception{
        Person p=service.people().getFirst();String photo=samplePhoto();String vector=Arrays.toString(RulesTest.face(0));
        String body="""
            {"registro":{"dni":"12345678","nombre":"Ana Pérez","foto":"%s","fotoRostro":"%s","modelo":"%s","vectores":[%s,%s,%s],"origenRostro":"CAMARA","revisionVisual":false,"version":"%s","eliminarHuella":false},"version":"%s"}
            """.formatted(photo,photo,MODEL,vector,vector,vector,p.version(),p.version());
        var http=MockMvcBuilders.webAppContextSetup(web).build();
        http.perform(post("/api/personas/validar").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        String fixed="""
            {"registro":{"dni":"12345678","nombre":"Ana Pérez","foto":"%s","fotoRostro":"%s","modelo":"%s","vectores":[%s,%s,%s],"origenRostro":"CAMARA","revisionVisual":false},"version":"%s"}
            """.formatted(photo,photo,MODEL,vector,vector,vector,p.version());
        http.perform(post("/api/personas/validar").contentType("application/json").content(fixed))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(true));
        assertEquals(p,service.people().getFirst());assertEquals("",db.queryForObject("SELECT foto_rostro FROM reconocimiento.persona WHERE dni='12345678'",String.class));
        String update="""
            {"nombre":"Ana Pérez","foto":"%s","fotoRostro":"%s","modelo":"%s","vectores":[%s,%s,%s],"origenRostro":"CAMARA","revisionVisual":false,"version":"%s","eliminarHuella":false}
            """.formatted(photo,photo,MODEL,vector,vector,vector,p.version());
        http.perform(put("/api/personas/12345678").contentType("application/json").content(update))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rostro").value(true)).andExpect(jsonPath("$.foto").value(photo));
        assertEquals(photo,db.queryForObject("SELECT foto_rostro FROM reconocimiento.persona WHERE dni='12345678'",String.class));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM reconocimiento.rostro WHERE dni='12345678'",Integer.class));
        assertTrue(service.attendance(null).isEmpty());
        http.perform(post("/api/personas/validar").contentType("application/json").content(fixed))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PERSONA_CONFLICTO"));
    }
    String publicKey(KeyPair keys){return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());}
    void bindExisting(KeyPair keys)throws Exception{
        Challenge challenge=service.challenge(new ChallengeRequest("VINCULAR","12345678",publicKey(keys),null),IP);
        service.bind(new Proof(challenge.id(),sign(challenge,keys),MODEL,RulesTest.face(0)),IP);
    }
    String samplePhoto()throws Exception{
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }
    boolean attempt(Challenge c){try{service.mark(faceProof(c),IP);return true;}catch(RuleViolation e){return false;}}
    String sign(Challenge c,KeyPair keys)throws Exception{Signature s=Signature.getInstance("SHA256withECDSA");s.initSign(keys.getPrivate());s.update(Rules.signedMessage(c).getBytes(java.nio.charset.StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(s.sign());}
}
