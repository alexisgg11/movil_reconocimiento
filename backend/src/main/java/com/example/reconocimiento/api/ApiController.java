package com.example.reconocimiento.api;

import com.example.reconocimiento.application.AttendanceService;
import static com.example.reconocimiento.domain.Models.*;
import com.example.reconocimiento.domain.RuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AttendanceService service;private final JdbcTemplate db;
    public ApiController(AttendanceService service,JdbcTemplate db){this.service=service;this.db=db;}
    @GetMapping("/health") public Map<String,Object> health(){db.queryForObject("SELECT 1",Integer.class);return Map.of("disponible",true,"aplicacion","reconocimiento","instancia",System.getProperty("app.instance","manual"),"version",System.getProperty("app.build","dev"));}
    @GetMapping("/configuracion") public Configuration configuration(){return service.configuration();}
    @GetMapping("/red") public Map<String,String> network(HttpServletRequest req){String ip=req.getRemoteAddr();return Map.of("ip",ip.equals("::1")||ip.equals("0:0:0:0:0:0:0:1")?"127.0.0.1":ip);}
    @PutMapping("/configuracion") public Configuration configure(@RequestBody Configuration c){return service.configure(c);}
    @GetMapping("/personas") public List<Person> people(){return service.people();}
    @GetMapping("/personas/{dni}/disponibilidad") public Map<String,Boolean> available(@PathVariable String dni){service.ensureDniAvailable(dni);return Map.of("disponible",true);}
    @PostMapping("/personas/validar") public Map<String,Boolean> validateEnrollment(@RequestBody EnrollmentValidation input){service.validateEnrollment(input);return Map.of("valido",true);}
    @PostMapping("/personas") public ResponseEntity<Person> register(@RequestBody Enrollment e,HttpServletRequest req){return ResponseEntity.status(201).body(service.register(e,req.getRemoteAddr()));}
    @PutMapping("/personas/{dni}") public Person update(@PathVariable String dni,@RequestBody PersonUpdate input,HttpServletRequest req){return service.update(dni,input,req.getRemoteAddr());}
    @PutMapping("/personas/{dni}/rostro") public Person replace(@PathVariable String dni,@RequestBody Enrollment e){return service.replace(dni,e);}
    @DeleteMapping("/personas/{dni}") public void remove(@PathVariable String dni){service.remove(dni);}
    @DeleteMapping("/personas/{dni}/dispositivo") public void unbind(@PathVariable String dni){service.unbindPerson(dni);}
    public record ChallengeResponse(String id,String nonce,String proposito,String dni,String nombre,boolean requiereRostro) {}
    @PostMapping("/retos") public ChallengeResponse challenge(@RequestBody ChallengeRequest r,HttpServletRequest req){Challenge c=service.challenge(r,req.getRemoteAddr());String name=c.dni()==null?null:service.people().stream().filter(p->p.dni().equals(c.dni())).map(Person::nombre).findFirst().orElse(null);return new ChallengeResponse(c.id(),c.nonce(),c.proposito(),c.dni(),name,service.requiresFace(c));}
    @PostMapping("/dispositivos/vincular") public Person bind(@RequestBody Proof p,HttpServletRequest req){return service.bind(p,req.getRemoteAddr());}
    @PostMapping("/dispositivos/consultar") public Device device(@RequestBody Map<String,String> body){return service.device(body.get("clave"));}
    @PostMapping("/asistencias") public Attendance mark(@RequestBody Proof p,HttpServletRequest req){return service.mark(p,req.getRemoteAddr());}
    @GetMapping("/asistencias") public List<Attendance> attendance(@RequestParam(required=false) LocalDate fecha){return service.attendance(fecha);}
    @GetMapping("/asistencias/reporte") public List<Attendance> report(@RequestParam(required=false) LocalDate desde,@RequestParam(required=false) LocalDate hasta){return service.report(desde,hasta);}
    @GetMapping("/asistencias/reporte.xlsx") public ResponseEntity<byte[]> workbook(@RequestParam(required=false) LocalDate desde,@RequestParam(required=false) LocalDate hasta) throws java.io.IOException {
        return ResponseEntity.ok().header("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header("Content-Disposition","attachment; filename=asistencias.xlsx").header("Cache-Control","no-store")
            .body(AttendanceWorkbook.create(service.report(desde,hasta)));
    }
}

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(RuleViolation.class) ResponseEntity<?> rule(RuleViolation e){return ResponseEntity.status(e.status).body(Map.of("codigo",e.code,"mensaje",e.getMessage()));}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("codigo","CONFLICTO","mensaje","El registro ya existe o fue modificado. Actualiza la pantalla."));}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("codigo","DATOS_INVALIDOS","mensaje","Los datos enviados no son válidos."));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e){org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("Error procesando solicitud",e);return ResponseEntity.internalServerError().body(Map.of("codigo","ERROR_SERVIDOR","mensaje","No se pudo completar la operación. Intenta nuevamente."));}
}
