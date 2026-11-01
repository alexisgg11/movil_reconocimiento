package com.example.reconocimiento.application;

import com.example.reconocimiento.domain.*;
import static com.example.reconocimiento.domain.Models.*;
import static com.example.reconocimiento.domain.Rules.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class AttendanceService {
    private final AttendanceStore store;
    private final Clock clock;
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    public AttendanceService(AttendanceStore store,Clock clock) {this.store=store;this.clock=clock;}
    public Configuration configuration(){return store.configuration(false);}
    public Configuration configure(Configuration c){config(c);Configuration current=store.configuration(true);if(c.version()!=null&&!c.version().isBlank())require(current.version().equals(c.version()),"CONFIG_CONFLICTO","La configuración cambió en otro lugar. Actualiza los datos antes de guardar.");store.saveConfiguration(c);return configuration();}
    public List<Person> people(){return store.people();}
    public void ensureDniAvailable(String dni){
        require(dni!=null&&dni.matches("[0-9]{8}"),"DNI_INVALIDO","El DNI debe tener ocho dígitos.");
        require(!store.dniExists(dni),"DNI_DUPLICADO","Ese DNI ya existe. Abre su ficha para editarlo; los DNI eliminados conservan su historial.");
    }
    public void validateEnrollment(EnrollmentValidation input){
        require(input!=null&&input.registro()!=null,"DATOS_INVALIDOS","Completa los datos de la persona.");
        Enrollment e=input.registro();
        if(input.version()==null||input.version().isBlank())ensureDniAvailable(e.dni());
        else require(active(e.dni()).version().equals(input.version()),"PERSONA_CONFLICTO","La ficha cambió. Ciérrala y vuelve a abrirla.");
        require(e.vectores()!=null,"ROSTRO_INVALIDO","Selecciona o captura un rostro.");
        validate(e);
    }
    private Person active(String dni){Person p=store.person(dni,true);require(p!=null,"PERSONA_NO_EXISTE","La persona no existe o fue eliminada.");return p;}
    private Enrollment validate(Enrollment e){
        require(e!=null&&e.dni()!=null&&e.dni().matches("[0-9]{8}"),"DNI_INVALIDO","El DNI debe tener ocho dígitos.");
        String normalizedName=name(e.nombre());
        photo(e.foto());
        if(e.vectores()==null)return new Enrollment(e.dni(),normalizedName,e.foto(),null,null,e.huella());
        require(MODEL.equals(e.modelo()),"MODELO_INVALIDO","Actualiza la aplicación para utilizar el modelo facial configurado.");
        String source=e.origenRostro()==null?"CAMARA":e.origenRostro();
        require(Set.of("CAMARA","GALERIA").contains(source),"ORIGEN_INVALIDO","El origen del rostro no es válido.");
        if(source.equals("GALERIA")){
            require(e.vectores().size()==1,"CAPTURAS_INVALIDAS","La foto debe generar una única plantilla facial.");
            require(Boolean.TRUE.equals(e.revisionVisual()),"REVISION_REQUERIDA","Confirma que la foto no tiene lentes ni objetos sobre el rostro.");
            require(e.fotoRostro()!=null&&!e.fotoRostro().isBlank(),"FOTO_REQUERIDA","La foto del rostro es obligatoria.");
            require(e.foto()!=null&&!e.foto().isBlank(),"FOTO_REQUERIDA","El recorte de la foto es obligatorio.");
        } else require(e.vectores().size()>=3&&e.vectores().size()<=5,"CAPTURAS_INSUFICIENTES","Registra entre tres y cinco capturas del rostro.");
        if(e.fotoRostro()!=null)photo(e.fotoRostro());
        List<double[]> vectors=e.vectores().stream().map(Rules::normalize).toList();
        for(double[] v:vectors)require(cosine(vectors.getFirst(),v)>=0.8,"CAPTURAS_DISTINTAS","Las capturas no corresponden al mismo rostro. Repite el registro.");
        double duplicateThreshold=Math.min(0.85,store.configuration(false).umbral());
        for(Template other:store.templates())if(!other.dni().equals(e.dni()))for(double[] v:vectors)
            require(cosine(v,normalize(other.vector()))<duplicateThreshold,"ROSTRO_DUPLICADO","Ese rostro coincide con una persona registrada. Revisa su ficha.");
        return new Enrollment(e.dni(),normalizedName,e.foto(),MODEL,vectors,e.huella(),e.fotoRostro(),source,e.revisionVisual());
    }
    public Person register(Enrollment input){return register(input,"127.0.0.1");}
    public Person register(Enrollment input,String ip){
        store.configuration(true);Enrollment e=validate(input);require(!store.dniExists(e.dni()),"DNI_DUPLICADO","El DNI ya existe, incluso si fue eliminado.");
        Challenge fingerprint=e.huella()==null?null:bindingProof(e.huella(),e.dni(),"ALTA_HUELLA",ip);
        store.insertPerson(e);
        if(fingerprint!=null){store.bind(new Device(deviceId(fingerprint.clave()),e.dni(),fingerprint.clave()));store.consume(fingerprint.id());}
        return active(e.dni());
    }
    public Person update(String dni,PersonUpdate input,String ip){
        store.configuration(true);Person current=active(dni);
        require(input!=null&&current.version().equals(input.version()),"PERSONA_CONFLICTO","La persona cambió en otro lugar. Cierra la ficha y vuelve a abrirla antes de guardar.");
        String normalizedName=name(input.nombre());String updatedPhoto=input.foto()==null?current.foto():input.foto();photo(updatedPhoto);
        require(!(Boolean.TRUE.equals(input.eliminarHuella())&&input.huella()!=null),"HUELLA_INVALIDA","Elige registrar o retirar la huella.");
        Challenge fingerprint=input.huella()==null?null:bindingProof(input.huella(),dni,"EDITAR_HUELLA",ip);
        if(input.vectores()!=null){Enrollment e=validate(new Enrollment(dni,normalizedName,updatedPhoto,input.modelo(),input.vectores(),null,input.fotoRostro(),input.origenRostro(),input.revisionVisual()));store.replaceFaces(e);}
        else store.updateProfile(dni,normalizedName,updatedPhoto);
        if(Boolean.TRUE.equals(input.eliminarHuella()))store.unbind(dni);
        if(fingerprint!=null){store.unbind(dni);store.bind(new Device(deviceId(fingerprint.clave()),dni,fingerprint.clave()));store.consume(fingerprint.id());}
        return active(dni);
    }
    private Challenge bindingProof(Proof proof,String dni,String purpose,String ip){
        Challenge c=validChallenge(proof.reto(),ip);
        require(c.proposito().equals(purpose)&&dni.equals(c.dni()),"RETO_INVALIDO","La verificación de huella no corresponde a esta persona.");
        Device existing=store.device(deviceId(c.clave()));
        require(existing==null||existing.dni().equals(dni),"DISPOSITIVO_VINCULADO","Este teléfono está vinculado a otra persona.");
        if(!purpose.equals("ALTA_HUELLA")&&existing==null&&active(dni).rostro()){
            require(MODEL.equals(proof.modelo()),"MODELO_INVALIDO","Vuelve a verificar el rostro.");
            Match match=identify(proof.vector(),store.templates(),store.configuration(false).umbral());
            require(dni.equals(match.dni()),"ROSTRO_NO_CORRESPONDE","El rostro no corresponde a la persona que estás editando.");
        }
        signature(c,proof.firma());return c;
    }
    public boolean requiresFace(Challenge c){return c.proposito().equals("EDITAR_HUELLA")&&active(c.dni()).rostro()&&store.device(deviceId(c.clave()))==null;}
    public Person replace(String dni,Enrollment input){store.configuration(true);active(dni);require(input!=null&&dni.equals(input.dni()),"DNI_INVALIDO","El DNI no se puede cambiar.");require(input.vectores()!=null,"CAPTURAS_INSUFICIENTES","Registra entre tres y cinco capturas del rostro.");Enrollment e=validate(input);store.replaceFaces(e);return active(dni);}
    public void remove(String dni){active(dni);store.deletePerson(dni);}
    public void unbindPerson(String dni){active(dni);store.unbind(dni);}
    public Device device(String key){String id=deviceId(key);Device d=store.device(id);require(d!=null,"DISPOSITIVO_SIN_VINCULAR","No hay una persona activa vinculada a este teléfono. No existe, fue eliminada o falta vincularla desde Personas.");active(d.dni());Device current=store.device(id);require(current!=null&&current.dni().equals(d.dni())&&current.clave().equals(key),"DISPOSITIVO_REVOCADO","La vinculación del teléfono cambió. Vuelve a registrar la huella desde su ficha.");return current;}
    public Challenge challenge(ChallengeRequest request,String ip){
        require(request!=null&&request.proposito()!=null&&Set.of("FACIAL","HUELLA","VINCULAR","ALTA_HUELLA","EDITAR_HUELLA").contains(request.proposito()),"RETO_INVALIDO","La operación no es válida.");
        Configuration c=store.configuration(true);String dni=null;String key="";
        if(!request.proposito().equals("FACIAL")){
            key=request.clave();require(key!=null&&key.length()<1000,"CLAVE_INVALIDA","No se recibió una clave válida.");String id=deviceId(key);
            if(request.proposito().equals("HUELLA"))dni=device(key).dni();
            else if(request.proposito().equals("ALTA_HUELLA")){
                require(request.dni()!=null&&request.dni().matches("[0-9]{8}"),"DNI_INVALIDO","Completa el DNI antes de registrar la huella.");
                require(!store.dniExists(request.dni()),"DNI_DUPLICADO","Ese DNI ya existe. Abre su ficha para editarlo.");
                require(store.device(id)==null,"DISPOSITIVO_VINCULADO","Este teléfono ya está vinculado a otra persona.");dni=request.dni();
            }
            else {dni=active(request.dni()).dni();Device current=store.device(id);require(current==null||(request.proposito().equals("EDITAR_HUELLA")&&current.dni().equals(dni)),"DISPOSITIVO_VINCULADO","Este teléfono ya está vinculado a otra persona.");if(request.proposito().equals("VINCULAR"))require(!active(dni).huella(),"PERSONA_VINCULADA","Esta persona ya tiene un dispositivo vinculado.");}
        }
        if(Set.of("FACIAL","HUELLA").contains(request.proposito())) {
            require(request.ubicacion()!=null&&request.ubicacion().edadMs()<=15000,"UBICACION_ANTIGUA","Actualiza tu ubicación antes de marcar.");zone(c,request.ubicacion(),0,ip);
        }
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        Challenge result=new Challenge(UUID.randomUUID().toString(),Base64.getUrlEncoder().withoutPadding().encodeToString(random),request.proposito(),dni,key,clock.instant(),request.ubicacion(),ip,c.revision());
        store.insertChallenge(result);return result;
    }
    private Challenge validChallenge(String id,String ip){
        Challenge c=store.challenge(id);require(c!=null,"RETO_INVALIDO","La solicitud ya se utilizó o no existe. Intenta nuevamente.");
        validateAge(c,clock.instant());
        require(c.ip().equals(ip),"CONEXION_CAMBIO","La conexión cambió durante la verificación. Intenta nuevamente.");return c;
    }
    private void validateAge(Challenge c,Instant instant){long age=Duration.between(c.creado(),instant).toMillis();long limit=Set.of("FACIAL","HUELLA").contains(c.proposito())?45000:300000;require(age>=0&&age<=limit,"RETO_VENCIDO","La verificación venció. Registra la huella o repite la marcación nuevamente.");}
    public Person bind(Proof proof,String ip){
        Challenge c=validChallenge(proof.reto(),ip);require(c.proposito().equals("VINCULAR"),"RETO_INVALIDO","La solicitud no corresponde a una vinculación.");
        Person p=active(c.dni());require(!p.huella(),"PERSONA_VINCULADA","La persona ya tiene un dispositivo vinculado.");
        require(MODEL.equals(proof.modelo()),"MODELO_INVALIDO","El modelo facial no coincide.");
        Match m=identify(proof.vector(),store.templates(),store.configuration(false).umbral());require(m.dni().equals(p.dni()),"ROSTRO_NO_CORRESPONDE","El rostro no corresponde a la persona seleccionada.");
        signature(c,proof.firma());store.bind(new Device(deviceId(c.clave()),p.dni(),c.clave()));store.consume(c.id());return active(p.dni());
    }
    public Attendance mark(Proof proof,String ip){
        Challenge c=validChallenge(proof.reto(),ip);require(Set.of("FACIAL","HUELLA").contains(c.proposito()),"RETO_INVALIDO","La solicitud no corresponde a una asistencia.");
        Configuration cfg=store.configuration(true);require(cfg.revision()==c.revision(),"CONFIG_CAMBIO","La zona cambió durante la verificación. Intenta nuevamente.");
        double dist=zone(cfg,c.ubicacion(),Duration.between(c.creado(),clock.instant()).toMillis(),ip);
        String dni;Double score=null;
        if(c.proposito().equals("FACIAL")){require(MODEL.equals(proof.modelo()),"MODELO_INVALIDO","El modelo facial no coincide.");Match m=identify(proof.vector(),store.templates(),cfg.umbral());dni=m.dni();score=m.score();}
        else {Device d=device(c.clave());require(d.dni().equals(c.dni()),"DISPOSITIVO_REVOCADO","La vinculación del dispositivo cambió.");signature(c,proof.firma());dni=d.dni();}
        Person p=active(dni);
        String captured="";String reference="";String referenceType="FICHA";
        if(score!=null){captured=proof.foto()==null?"":proof.foto();photo(captured);reference=store.facePhoto(dni);if(!reference.isBlank())referenceType="GALERIA".equals(p.origenRostro())?"GALERIA":"ROSTRO";else reference=p.foto();}
        Instant markedAt=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        validateAge(c,markedAt);dist=zone(cfg,c.ubicacion(),Duration.between(c.creado(),markedAt).toMillis(),ip);
        LocalDate date=markedAt.atZone(LIMA).toLocalDate();require(!store.attended(dni,date),"YA_MARCO","Esta persona ya marcó su asistencia hoy.");
        Attendance result=store.insertAttendance(new Attendance(0,dni,p.nombre(),date,markedAt,c.proposito(),dist,c.ubicacion().precision(),ip,score,captured,reference,score==null?null:cfg.umbral(),referenceType));store.consume(c.id());return result;
    }
    public List<Attendance> attendance(LocalDate date){return store.attendance(date==null?LocalDate.now(clock.withZone(LIMA)):date);}
    public List<Attendance> report(LocalDate from,LocalDate to){
        require((from==null)==(to==null),"PERIODO_INVALIDO","Selecciona las dos fechas del reporte.");
        if(from!=null)require(!from.isAfter(to)&&from.getYear()>=1900&&!to.isAfter(LocalDate.now(clock.withZone(LIMA))),"PERIODO_INVALIDO","Elige un período válido, sin fechas futuras.");
        return store.report(from,to);
    }
}
