package com.example.reconocimiento.api;

import com.example.reconocimiento.application.VerificationService;
import static com.example.reconocimiento.domain.Models.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verificacion")
public class VerificationController {
    private final VerificationService service;
    public VerificationController(VerificationService service){this.service=service;}
    @PostMapping("/ubicacion") public ZoneCheck zone(@RequestBody Position p,HttpServletRequest request){return service.zoneCheck(p,request.getRemoteAddr());}
    @PostMapping("/rostro") public FaceTestResult face(@RequestBody FaceTestRequest request){return service.faceTest(request);}
    @GetMapping("/resumen") public TrialSummary summary(@RequestParam(defaultValue="legacy-v1") String capturaVersion){return service.summary(capturaVersion);}
}
