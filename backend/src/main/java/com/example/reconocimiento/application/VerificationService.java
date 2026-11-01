package com.example.reconocimiento.application;

import com.example.reconocimiento.domain.*;
import static com.example.reconocimiento.domain.Models.*;
import static com.example.reconocimiento.domain.Rules.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class VerificationService {
    private final AttendanceStore store;
    private final Clock clock;
    public VerificationService(AttendanceStore store,Clock clock){this.store=store;this.clock=clock;}
    public ZoneCheck zoneCheck(Position p,String ip){
        Configuration c=store.configuration(false);Double distance=null;
        if(p!=null&&c.latitud()!=null&&c.longitud()!=null&&Double.isFinite(p.latitud())&&Double.isFinite(p.longitud()))
            distance=distance(p.latitud(),p.longitud(),c.latitud(),c.longitud());
        try {
            require(p!=null&&p.edadMs()<=15000,"UBICACION_ANTIGUA","Actualiza la ubicación para comprobar la zona.");
            zone(c,p,0,ip);
            return new ZoneCheck(true,"PERMITIDA","Puedes marcar desde esta ubicación.",distance,p.precision(),c.radio(),allowed(c.red(),ip),ip);
        } catch(RuleViolation e){return new ZoneCheck(false,e.code,e.getMessage(),distance,p==null?0:p.precision(),c.radio(),allowed(c.red(),ip),ip);}
    }
    private String galleryVersion(List<Template> templates){return version(templates.stream().map(t->t.dni()+":"+Arrays.toString(t.vector())).sorted().collect(Collectors.joining("|")));}
    private String capturePolicy(String value){String policy=value==null?"legacy-v1":value;require(Set.of("legacy-v1","frontal-estable-v2","frontal-accesorios-v3","guiada-manual-v4","guiada-automatica-v5",CAPTURE_POLICY).contains(policy),"CAPTURA_INVALIDA","Actualiza la aplicación para utilizar la captura configurada.");return policy;}
    public TrialSummary summary(){return summary(CAPTURE_POLICY);}
    public TrialSummary summary(String policy){return store.trialSummary(galleryVersion(store.templates()),store.configuration(false).umbral(),capturePolicy(policy));}
    public FaceTestResult faceTest(FaceTestRequest request){
        require(request!=null&&MODEL.equals(request.modelo()),"MODELO_INVALIDO","El modelo facial no coincide.");
        String policy=capturePolicy(request.capturaVersion());
        Configuration cfg=store.configuration(true);List<Template> templates=store.templates();
        require(!templates.isEmpty(),"SIN_ROSTROS","Registra al menos un rostro antes de iniciar las pruebas.");
        String expected=request.dniEsperado();
        if(expected!=null){Person p=store.person(expected,true);require(p!=null&&p.rostro(),"PERSONA_NO_EXISTE","Selecciona una persona activa con rostro registrado.");}
        List<Match> ranked=ranking(request.vector(),templates);Match best=ranked.getFirst();
        double margin=ranked.size()>1?best.score()-ranked.get(1).score():2;
        String matched=best.score()>=cfg.umbral()&&margin>=.04?best.dni():null;
        Double expectedScore=expected==null?null:ranked.stream().filter(m->m.dni().equals(expected)).findFirst().orElseThrow().score();
        String outcome=expected==null?(matched==null?"RECHAZO_CORRECTO":"FALSA_ACEPTACION"):
            (matched==null?"FALSO_RECHAZO":expected.equals(matched)?"COINCIDENCIA_CORRECTA":"IDENTIDAD_INCORRECTA");
        FaceTrial trial=new FaceTrial(expected,matched,best.score(),expectedScore,cfg.umbral(),margin,outcome,clock.instant(),galleryVersion(templates),policy);
        store.insertTrial(trial);
        return new FaceTestResult(trial,store.trialSummary(trial.registroVersion(),trial.umbral(),policy));
    }
}
