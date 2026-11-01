package com.example.reconocimiento.domain;

import static com.example.reconocimiento.domain.Models.*;
import java.net.InetAddress;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

public final class Rules {
    private Rules() {}
    public static String version(Object... values) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String name(String value) {
        require(value!=null,"NOMBRE_INVALIDO","Escribe el nombre completo.");
        String name=value.strip().replaceAll("\\s+"," ");
        require(name.length()>=3&&name.length()<=120&&name.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .’'-]+"),"NOMBRE_INVALIDO","El nombre debe contener letras y tener entre 3 y 120 caracteres.");
        return name;
    }
    public static void photo(String photo) {
        require(photo!=null&&photo.length()<=350000,"FOTO_INVALIDA","La fotografía no es válida o es demasiado grande.");
        if(photo.isEmpty())return;
        try {
            byte[] bytes=Base64.getDecoder().decode(photo);
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                require(readers.hasNext(),"FOTO_INVALIDA","Selecciona una fotografía JPEG o PNG válida.");
                var reader=readers.next();
                try {reader.setInput(stream);String type=reader.getFormatName();require((type.equalsIgnoreCase("JPEG")||type.equalsIgnoreCase("PNG"))&&reader.getWidth(0)<=2048&&reader.getHeight(0)<=2048,"FOTO_INVALIDA","La fotografía debe ser JPEG o PNG de hasta 2048 píxeles.");var decoded=reader.read(0);require(decoded!=null,"FOTO_INVALIDA","No se pudo leer la fotografía.");decoded.flush();}
                finally {reader.dispose();}
            }
        } catch(RuleViolation e){throw e;} catch(Exception e){throw new RuleViolation("FOTO_INVALIDA","No se pudo validar la fotografía.");}
    }
    public static void require(boolean valid, String code, String message) {
        if (!valid) throw new RuleViolation(code, message);
    }
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)
            + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, a))));
    }
    public static void coordinates(double lat, double lon) {
        require(Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat)<=90 && Math.abs(lon)<=180,
            "UBICACION_INVALIDA", "La ubicación no es válida.");
    }
    public static void config(Configuration c) {
        require(c != null && c.nombre()!=null && !c.nombre().isBlank() && c.nombre().length()<=100, "CONFIG_INVALIDA", "Escribe el nombre de la sede.");
        require(c.latitud()!=null && c.longitud()!=null, "ZONA_SIN_CONFIGURAR", "Configura la ubicación de la sede.");
        coordinates(c.latitud(),c.longitud());
        require(Double.isFinite(c.radio()) && c.radio()>=1 && c.radio()<=10000, "RADIO_INVALIDO", "El radio debe estar entre 1 y 10 000 metros.");
        require(Double.isFinite(c.umbral()) && c.umbral()>=0.8 && c.umbral()<=0.99, "UMBRAL_INVALIDO", "La similitud mínima debe estar entre 80% y 99%.");
        network(c.red());
    }
    public static double zone(Configuration c, Position p, long extraAge, String ip) {
        require(c.latitud()!=null && c.longitud()!=null, "ZONA_SIN_CONFIGURAR", "Configura la ubicación de la sede antes de marcar.");
        require(p!=null, "UBICACION_REQUERIDA", "Permite el acceso a tu ubicación precisa para marcar.");
        coordinates(p.latitud(),p.longitud());
        require(!p.simulada(), "UBICACION_SIMULADA", "Desactiva la ubicación simulada para marcar.");
        require(p.edadMs()>=0 && extraAge>=0 && p.edadMs()+extraAge<=60000, "UBICACION_ANTIGUA", "Actualiza tu ubicación e intenta nuevamente.");
        require(Double.isFinite(p.precision()) && p.precision()>0 && p.precision()<=25, "UBICACION_IMPRECISA", "La ubicación no tiene suficiente precisión. Acércate a una ventana o sal al exterior.");
        double d = distance(p.latitud(),p.longitud(),c.latitud(),c.longitud());
        require(d+p.precision()<=c.radio(), "FUERA_DE_ZONA", "No puedes marcar: estás fuera de la zona o la precisión no permite confirmar el límite de " + Math.round(c.radio()) + " m.");
        require(allowed(c.red(),ip), "IP_NO_PERMITIDA", "La conexión no pertenece a la IP o red permitida.");
        return d;
    }
    private static long ipv4(String s) {
        String[] parts=s.split("\\.",-1); if(parts.length!=4) throw new IllegalArgumentException();
        long n=0;
        for(String x:parts) { if(!x.matches("0|[1-9][0-9]{0,2}")) throw new IllegalArgumentException(); int v=Integer.parseInt(x); if(v>255)throw new IllegalArgumentException();n=(n<<8)|v; }
        return n;
    }
    public static void network(String s) {
        if(s==null || s.isBlank()) return;
        try { String[] p=s.split("/",-1); if(p.length>2)throw new IllegalArgumentException(); ipv4(p[0]); if(p.length==2 && (!p[1].matches("[0-9]{1,2}") || Integer.parseInt(p[1])>32))throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw new RuleViolation("RED_INVALIDA","Usa una IPv4 o una red CIDR, por ejemplo 192.168.1.0/24."); }
    }
    public static boolean allowed(String network,String ip) {
        if(network==null||network.isBlank())return true;
        try { if(ip.startsWith("::ffff:"))ip=ip.substring(7); if(ip.equals("::1")||ip.equals("0:0:0:0:0:0:0:1"))ip="127.0.0.1";
            String[] p=network.split("/"); int bits=p.length==1?32:Integer.parseInt(p[1]);long mask=bits==0?0:(0xffffffffL<<(32-bits))&0xffffffffL;
            return (ipv4(ip)&mask)==(ipv4(p[0])&mask);
        } catch(Exception e) { return false; }
    }
    public static double[] normalize(double[] vector) {
        require(vector!=null && vector.length==512,"ROSTRO_INVALIDO","Vuelve a capturar el rostro con el modelo configurado.");
        double norm=0; for(double d:vector){require(Double.isFinite(d),"ROSTRO_INVALIDO","La captura facial no es válida.");norm+=d*d;}
        require(norm>1e-12,"ROSTRO_INVALIDO","La captura facial no contiene características válidas.");
        double[] result=vector.clone(); norm=Math.sqrt(norm);for(int i=0;i<result.length;i++)result[i]/=norm;return result;
    }
    public static double cosine(double[] a,double[] b) { double n=0;for(int i=0;i<a.length;i++)n+=a[i]*b[i];return Math.max(-1,Math.min(1,n)); }
    public static List<Match> ranking(double[] vector,List<Template> templates) {
        double[] query=normalize(vector);Map<String,Double> scores=new HashMap<>();
        for(Template t:templates)scores.merge(t.dni(),cosine(query,normalize(t.vector())),Math::max);
        return scores.entrySet().stream().map(e->new Match(e.getKey(),e.getValue())).sorted(Comparator.comparingDouble(Match::score).reversed()).toList();
    }
    public static Match identify(double[] vector,List<Template> templates,double threshold) {
        List<Match> matches=ranking(vector,templates);
        require(!matches.isEmpty()&&matches.getFirst().score()>=threshold,"PERSONA_NO_RECONOCIDA","Persona no reconocida: no hay una coincidencia válida. Comprueba que exista, tenga rostro registrado y repite con buena iluminación.");
        require(matches.size()<2||matches.getFirst().score()-matches.get(1).score()>=0.04,"ROSTRO_AMBIGUO","Hay una coincidencia ambigua. Repite la captura con mejor iluminación.");
        return matches.getFirst();
    }
    public static String deviceId(String publicKey) {
        try { byte[] bytes=Base64.getDecoder().decode(publicKey); KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(bytes));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new RuleViolation("CLAVE_INVALIDA","No se pudo validar la clave del dispositivo."); }
    }
    public static String signedMessage(Challenge c) { return "RECONOCIMIENTO/1|"+c.id()+"|"+c.nonce()+"|"+c.proposito(); }
    public static void signature(Challenge c,String signature) {
        try { PublicKey key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(c.clave())));
            Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(signedMessage(c).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            require(signature!=null&&verifier.verify(Base64.getDecoder().decode(signature)),"FIRMA_INVALIDA","No se pudo verificar la autenticación del dispositivo.");
        } catch(RuleViolation e) { throw e; } catch(Exception e) { throw new RuleViolation("FIRMA_INVALIDA","No se pudo verificar la autenticación del dispositivo."); }
    }
}
