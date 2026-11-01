package com.example.reconocimiento;

import com.example.reconocimiento.domain.*;
import static com.example.reconocimiento.domain.Models.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.security.*;
import java.time.Instant;
import java.util.*;

class RulesTest {
    Configuration zone(double radius){return new Configuration("Sede",0d,0d,radius,"",.8,1);}
    @Test void theAccuracyNeverExtendsTheRadius(){
        double lon=Math.toDegrees(95d/6371000);
        Position p=new Position(0,lon,5,0,false);
        assertDoesNotThrow(()->Rules.zone(zone(100.000001),p,0,"127.0.0.1"));
        assertThrows(RuleViolation.class,()->Rules.zone(zone(99.99),p,0,"127.0.0.1"));
        assertThrows(RuleViolation.class,()->Rules.zone(zone(100),new Position(0,Math.toDegrees(101d/6371000),1,0,false),0,"127.0.0.1"));
    }
    @Test void rejectsMissingStaleMockAndNonFiniteLocations(){
        assertThrows(RuleViolation.class,()->Rules.zone(zone(100),null,0,"127.0.0.1"));
        for(Position p:List.of(new Position(0,0,5,0,true),new Position(0,0,5,60001,false),new Position(0,0,26,0,false),new Position(Double.NaN,0,5,0,false),new Position(0,0,0,0,false)))
            assertThrows(RuleViolation.class,()->Rules.zone(zone(100),p,0,"127.0.0.1"));
        assertThrows(RuleViolation.class,()->Rules.zone(new Configuration("Sin zona",null,null,100,"",.8,0),new Position(0,0,5,0,false),0,"127.0.0.1"));
    }
    @Test void validatesCidrWithoutResolvingHostnames(){
        assertTrue(Rules.allowed("192.168.1.0/24","192.168.1.255"));
        assertFalse(Rules.allowed("192.168.1.0/24","192.168.2.1"));
        assertTrue(Rules.allowed("127.0.0.1","::1"));
        assertFalse(Rules.allowed("192.168.1.2","192.168.1.3"));
        for(String s:List.of("example.com","999.1.1.1","1.1.1.1/33","1.1.1.1/","1.1.1.1/24/0"))assertThrows(RuleViolation.class,()->Rules.network(s));
    }
    static double[] face(int index){double[] v=new double[512];v[index]=1;return v;}
    @Test void unknownAndAmbiguousFacesAreNotAssignedToTheNearestPerson(){
        List<Template> gallery=List.of(new Template("12345678",face(0)));
        assertEquals("12345678",Rules.identify(face(0),gallery,.8).dni());
        assertThrows(RuleViolation.class,()->Rules.identify(face(1),gallery,.8));
        assertThrows(RuleViolation.class,()->Rules.identify(face(0),List.of(gallery.getFirst(),new Template("87654321",face(0))),.8));
        assertThrows(RuleViolation.class,()->Rules.normalize(new double[512]));
    }
    @Test void signaturesBindThePurposeAndNonce()throws Exception{
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(256);KeyPair keys=g.generateKeyPair();
        String pub=Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        Challenge c=new Challenge("id","nonce","HUELLA","12345678",pub,Instant.now(),null,"127.0.0.1",0);
        Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(keys.getPrivate());signer.update(Rules.signedMessage(c).getBytes(java.nio.charset.StandardCharsets.UTF_8));String proof=Base64.getEncoder().encodeToString(signer.sign());
        assertDoesNotThrow(()->Rules.signature(c,proof));
        assertThrows(RuleViolation.class,()->Rules.signature(new Challenge("id","different","HUELLA","12345678",pub,c.creado(),null,c.ip(),0),proof));
    }
}
