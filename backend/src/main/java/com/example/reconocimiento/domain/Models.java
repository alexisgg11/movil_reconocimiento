package com.example.reconocimiento.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Models {
    private Models() {}
    public static final String MODEL = "facenet512-fp16-v1";
    public static final String CAPTURE_POLICY = "frontal-unico-v6";
    public record Person(String dni, String nombre, String foto, boolean rostro, boolean huella, String version,String origenRostro) {}
    public record Enrollment(String dni, String nombre, String foto, String modelo, List<double[]> vectores, Proof huella,String fotoRostro,String origenRostro,Boolean revisionVisual) {
        public Enrollment(String dni,String nombre,String foto,String modelo,List<double[]> vectores,Proof huella,String fotoRostro){this(dni,nombre,foto,modelo,vectores,huella,fotoRostro,"CAMARA",false);}
        public Enrollment(String dni,String nombre,String foto,String modelo,List<double[]> vectores){this(dni,nombre,foto,modelo,vectores,null,null);}
        public Enrollment(String dni,String nombre,String foto,String modelo,List<double[]> vectores,Proof huella){this(dni,nombre,foto,modelo,vectores,huella,null);}
    }
    public record PersonUpdate(String nombre,String foto,String modelo,List<double[]> vectores,Proof huella,Boolean eliminarHuella,String version,String fotoRostro,String origenRostro,Boolean revisionVisual) {
        public PersonUpdate(String nombre,String foto,String modelo,List<double[]> vectores,Proof huella,Boolean eliminarHuella,String version,String fotoRostro){this(nombre,foto,modelo,vectores,huella,eliminarHuella,version,fotoRostro,"CAMARA",false);}
        public PersonUpdate(String nombre,String foto,String modelo,List<double[]> vectores,Proof huella,boolean eliminarHuella,String version){this(nombre,foto,modelo,vectores,huella,eliminarHuella,version,null);}
    }
    public record EnrollmentValidation(Enrollment registro,String version) {}
    public record Configuration(String nombre, Double latitud, Double longitud, double radio, String red, double umbral, long revision, String version) {
        public Configuration(String nombre,Double latitud,Double longitud,double radio,String red,double umbral,long revision){this(nombre,latitud,longitud,radio,red,umbral,revision,"");}
    }
    public record Position(double latitud, double longitud, double precision, long edadMs, boolean simulada) {}
    public record ChallengeRequest(String proposito, String dni, String clave, Position ubicacion) {}
    public record Challenge(String id, String nonce, String proposito, String dni, String clave, Instant creado,
                            Position ubicacion, String ip, long revision) {}
    public record Proof(String reto, String firma, String modelo, double[] vector, String foto) {
        public Proof(String reto,String firma,String modelo,double[] vector){this(reto,firma,modelo,vector,null);}
    }
    public record Template(String dni, double[] vector) {}
    public record Match(String dni, double score) {}
    public record Attendance(long id, String dni, String nombre, LocalDate fecha, Instant instante, String metodo,
                             double distancia, double precision, String ip, Double similitud,
                             String foto, String fotoRegistro, Double umbral,String tipoFotoRegistro) {}
    public record ZoneCheck(boolean permitida,String codigo,String mensaje,Double distancia,double precision,double radio,boolean redPermitida,String ip) {}
    public record FaceTestRequest(String modelo,double[] vector,String dniEsperado,String capturaVersion) {
        public FaceTestRequest(String modelo,double[] vector,String dniEsperado){this(modelo,vector,dniEsperado,CAPTURE_POLICY);}
    }
    public record FaceTrial(String dniEsperado,String dniReconocido,double similitud,Double similitudEsperada,double umbral,double margen,String resultado,Instant instante,String registroVersion,String capturaVersion) {}
    public record TrialSummary(long total,long correctos,long falsosRechazos,long falsasAceptaciones,long identidadesIncorrectas) {}
    public record FaceTestResult(FaceTrial prueba,TrialSummary resumen) {}
    public record Device(String id, String dni, String clave) {}
}
