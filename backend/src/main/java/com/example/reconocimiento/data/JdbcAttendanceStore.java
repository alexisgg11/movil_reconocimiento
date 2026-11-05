package com.example.reconocimiento.data;

import com.example.reconocimiento.domain.AttendanceStore;
import static com.example.reconocimiento.domain.Models.*;
import com.example.reconocimiento.domain.Rules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class JdbcAttendanceStore implements AttendanceStore {
    private final JdbcTemplate db;
    public JdbcAttendanceStore(JdbcTemplate db) { this.db=db; }
    private static final String PERSON_SQL="SELECT p.dni,p.nombre,p.foto,p.foto_rostro,p.origen_rostro, EXISTS(SELECT 1 FROM reconocimiento.rostro r WHERE r.dni=p.dni) rostro, EXISTS(SELECT 1 FROM reconocimiento.dispositivo d WHERE d.dni=p.dni) huella, COALESCE((SELECT d.id FROM reconocimiento.dispositivo d WHERE d.dni=p.dni),'') dispositivo FROM reconocimiento.persona p ";
    @Override public Configuration configuration(boolean lock) {
        return db.queryForObject("SELECT * FROM reconocimiento.configuracion WHERE id=1"+(lock?" FOR UPDATE":""),(r,n)->{
            Configuration c=new Configuration(r.getString("nombre"),(Double)r.getObject("latitud"),(Double)r.getObject("longitud"),r.getDouble("radio"),r.getString("red"),r.getDouble("umbral"),r.getLong("revision"));
            return new Configuration(c.nombre(),c.latitud(),c.longitud(),c.radio(),c.red(),c.umbral(),c.revision(),Rules.version(c));
        });
    }
    @Override public void saveConfiguration(Configuration c) {
        db.update("UPDATE reconocimiento.configuracion SET nombre=?,latitud=?,longitud=?,radio=?,red=?,umbral=?,revision=revision+1 WHERE id=1",c.nombre(),c.latitud(),c.longitud(),c.radio(),c.red(),c.umbral());
    }
    @Override public List<Person> people() { return db.query(PERSON_SQL+"WHERE p.activo=TRUE ORDER BY p.nombre",this::mapPerson); }
    private Person mapPerson(ResultSet r,int n)throws SQLException {return new Person(r.getString("dni"),r.getString("nombre"),r.getString("foto"),r.getBoolean("rostro"),r.getBoolean("huella"),Rules.version(r.getString("nombre"),r.getString("foto"),r.getBoolean("rostro"),r.getString("dispositivo"),r.getString("foto_rostro"),r.getString("origen_rostro")),r.getString("origen_rostro"));}
    @Override public Person person(String dni,boolean lock) {
        if(lock)db.query("SELECT dni FROM reconocimiento.persona WHERE dni=? FOR UPDATE",(r,n)->r.getString(1),dni);
        List<Person> p=db.query(PERSON_SQL+"WHERE p.dni=? AND p.activo=TRUE",this::mapPerson,dni);return p.isEmpty()?null:p.getFirst();
    }
    @Override public boolean dniExists(String dni) { return db.queryForObject("SELECT COUNT(*) FROM reconocimiento.persona WHERE dni=?",Integer.class,dni)>0; }
    @Override public void insertPerson(Enrollment e) {db.update("INSERT INTO reconocimiento.persona(dni,nombre,foto) VALUES(?,?,?)",e.dni(),e.nombre(),e.foto()==null?"":e.foto());if(e.vectores()!=null)replaceFaces(e);}
    @Override public void updateProfile(String dni,String name,String photo) {db.update("UPDATE reconocimiento.persona SET nombre=?,foto=? WHERE dni=?",name,photo,dni);}
    @Override public void replaceFaces(Enrollment e) {
        db.update("DELETE FROM reconocimiento.rostro WHERE dni=?",e.dni());
        for(double[] v:e.vectores())db.update("INSERT INTO reconocimiento.rostro(dni,vector,modelo) VALUES(?,?,?)",e.dni(),Arrays.toString(v),e.modelo());
        db.update("UPDATE reconocimiento.persona SET foto=?,nombre=?,foto_rostro=?,origen_rostro=? WHERE dni=?",e.foto(),e.nombre(),e.fotoRostro()==null?"":e.fotoRostro(),e.origenRostro(),e.dni());
        unbind(e.dni());
    }
    @Override public String facePhoto(String dni){return db.queryForObject("SELECT foto_rostro FROM reconocimiento.persona WHERE dni=?",String.class,dni);}
    @Override public void deletePerson(String dni) {db.update("DELETE FROM reconocimiento.rostro WHERE dni=?",dni);unbind(dni);db.update("UPDATE reconocimiento.persona SET activo=FALSE,foto='',foto_rostro='' WHERE dni=?",dni);}
    @Override public List<Template> templates() { return db.query("SELECT r.dni,r.vector FROM reconocimiento.rostro r JOIN reconocimiento.persona p ON p.dni=r.dni WHERE p.activo=TRUE AND r.modelo=?",(r,n)->new Template(r.getString(1),parse(r.getString(2))),MODEL); }
    private double[] parse(String s) {return Arrays.stream(s.substring(1,s.length()-1).split(",")).mapToDouble(Double::parseDouble).toArray();}
    @Override public void insertChallenge(Challenge c) {
        db.update("DELETE FROM reconocimiento.reto WHERE creado < ?",Timestamp.from(c.creado().minusSeconds(300)));
        Position p=c.ubicacion();
        db.update("INSERT INTO reconocimiento.reto(id,nonce,proposito,dni,clave,creado,latitud,longitud,precision_m,edad_ms,ip,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",c.id(),c.nonce(),c.proposito(),c.dni(),c.clave(),Timestamp.from(c.creado()),p==null?null:p.latitud(),p==null?null:p.longitud(),p==null?null:p.precision(),p==null?null:p.edadMs(),c.ip(),c.revision());
    }
    @Override public Challenge challenge(String id) {
        return readChallenge("SELECT * FROM reconocimiento.reto WHERE id=? AND usado=FALSE FOR UPDATE", id);
    }
    @Override public Challenge loginChallenge(String id) {
        return readChallenge("SELECT * FROM reconocimiento.reto WHERE id=? AND usado=FALSE AND LEFT(proposito,6)='LOGIN_' FOR UPDATE", id);
    }
    private Challenge readChallenge(String sql, String id) {
        List<Challenge> list=db.query(sql,(r,n)->new Challenge(r.getString("id"),r.getString("nonce"),r.getString("proposito"),r.getString("dni"),r.getString("clave"),r.getTimestamp("creado").toInstant(),r.getObject("latitud")==null?null:new Position(r.getDouble("latitud"),r.getDouble("longitud"),r.getDouble("precision_m"),r.getLong("edad_ms"),false),r.getString("ip"),r.getLong("revision")),id);
        return list.isEmpty()?null:list.getFirst();
    }
    @Override public void consume(String id) {db.update("UPDATE reconocimiento.reto SET usado=TRUE WHERE id=?",id);}
    @Override public Device device(String id) {List<Device> list=db.query("SELECT * FROM reconocimiento.dispositivo WHERE id=?",(r,n)->new Device(r.getString("id"),r.getString("dni"),r.getString("clave")),id);return list.isEmpty()?null:list.getFirst();}
    @Override public void bind(Device d) {db.update("INSERT INTO reconocimiento.dispositivo(id,dni,clave) VALUES(?,?,?)",d.id(),d.dni(),d.clave());}
    @Override public void unbind(String dni) {db.update("DELETE FROM reconocimiento.dispositivo WHERE dni=?",dni);}
    @Override public boolean attended(String dni,LocalDate date) {return db.queryForObject("SELECT COUNT(*) FROM reconocimiento.asistencia WHERE dni=? AND fecha=?",Integer.class,dni,java.sql.Date.valueOf(date))>0;}
    @Override public Attendance insertAttendance(Attendance a) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        db.update(con->{PreparedStatement s=con.prepareStatement("INSERT INTO reconocimiento.asistencia(dni,nombre,fecha,instante,metodo,distancia,precision_m,ip,similitud,foto,foto_registro,umbral,tipo_foto_registro) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});
            s.setString(1,a.dni());s.setString(2,a.nombre());s.setDate(3,java.sql.Date.valueOf(a.fecha()));s.setTimestamp(4,Timestamp.from(a.instante()));s.setString(5,a.metodo());s.setDouble(6,a.distancia());s.setDouble(7,a.precision());s.setString(8,a.ip());s.setObject(9,a.similitud());s.setString(10,a.foto());s.setString(11,a.fotoRegistro());s.setObject(12,a.umbral());s.setString(13,a.tipoFotoRegistro());return s;},keys);
        return new Attendance(keys.getKey().longValue(),a.dni(),a.nombre(),a.fecha(),a.instante(),a.metodo(),a.distancia(),a.precision(),a.ip(),a.similitud(),a.foto(),a.fotoRegistro(),a.umbral(),a.tipoFotoRegistro());
    }
    @Override public List<Attendance> attendance(LocalDate date) {
        return db.query("SELECT * FROM reconocimiento.asistencia WHERE fecha=? ORDER BY instante DESC", this::mapAttendance, java.sql.Date.valueOf(date));
    }
    @Override public List<Attendance> attendance(LocalDate date, String dni) {
        return db.query("SELECT * FROM reconocimiento.asistencia WHERE fecha=? AND dni=? ORDER BY instante DESC",
                this::mapAttendance, java.sql.Date.valueOf(date), dni);
    }
    private Attendance mapAttendance(ResultSet row, int index) throws SQLException {
        return new Attendance(row.getLong("id"), row.getString("dni"), row.getString("nombre"), row.getDate("fecha").toLocalDate(),
                row.getTimestamp("instante").toInstant(), row.getString("metodo"), row.getDouble("distancia"), row.getDouble("precision_m"),
                row.getString("ip"), (Double)row.getObject("similitud"), row.getString("foto"), row.getString("foto_registro"),
                (Double)row.getObject("umbral"), row.getString("tipo_foto_registro"));
    }
    @Override public List<Attendance> report(LocalDate from,LocalDate to) {
        String sql="SELECT id,dni,nombre,fecha,instante,metodo,distancia,precision_m,ip,similitud,umbral,tipo_foto_registro FROM reconocimiento.asistencia";
        if(from!=null)sql+=" WHERE fecha BETWEEN ? AND ?";
        sql+=" ORDER BY fecha DESC,instante DESC,id DESC";
        return db.query(sql,(r,n)->new Attendance(r.getLong("id"),r.getString("dni"),r.getString("nombre"),r.getDate("fecha").toLocalDate(),r.getTimestamp("instante").toInstant(),r.getString("metodo"),r.getDouble("distancia"),r.getDouble("precision_m"),r.getString("ip"),(Double)r.getObject("similitud"),"","",(Double)r.getObject("umbral"),r.getString("tipo_foto_registro")),from==null?new Object[0]:new Object[]{java.sql.Date.valueOf(from),java.sql.Date.valueOf(to)});
    }
    @Override public void insertTrial(FaceTrial t){db.update("INSERT INTO reconocimiento.ensayo_facial(dni_esperado,dni_reconocido,similitud,similitud_esperada,umbral,margen,resultado,instante,registro_version,modelo,captura_version) VALUES(?,?,?,?,?,?,?,?,?,?,?)",t.dniEsperado(),t.dniReconocido(),t.similitud(),t.similitudEsperada(),t.umbral(),t.margen(),t.resultado(),Timestamp.from(t.instante()),t.registroVersion(),MODEL,t.capturaVersion());}
    @Override public TrialSummary trialSummary(String version,double threshold,String capturePolicy){return db.queryForObject("SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN resultado IN ('COINCIDENCIA_CORRECTA','RECHAZO_CORRECTO') THEN 1 ELSE 0 END),0) correctos,COALESCE(SUM(CASE WHEN resultado='FALSO_RECHAZO' THEN 1 ELSE 0 END),0) rechazos,COALESCE(SUM(CASE WHEN resultado='FALSA_ACEPTACION' THEN 1 ELSE 0 END),0) aceptaciones,COALESCE(SUM(CASE WHEN resultado='IDENTIDAD_INCORRECTA' THEN 1 ELSE 0 END),0) identidades FROM reconocimiento.ensayo_facial WHERE registro_version=? AND umbral=? AND modelo=? AND captura_version=?",(r,n)->new TrialSummary(r.getLong("total"),r.getLong("correctos"),r.getLong("rechazos"),r.getLong("aceptaciones"),r.getLong("identidades")),version,threshold,MODEL,capturePolicy);}
}
