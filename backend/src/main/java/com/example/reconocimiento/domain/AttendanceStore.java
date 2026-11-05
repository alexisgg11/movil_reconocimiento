package com.example.reconocimiento.domain;

import static com.example.reconocimiento.domain.Models.*;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceStore {
    Configuration configuration(boolean lock);
    void saveConfiguration(Configuration c);
    List<Person> people();
    Person person(String dni, boolean lock);
    boolean dniExists(String dni);
    void insertPerson(Enrollment e);
    void updateProfile(String dni,String name,String photo);
    void replaceFaces(Enrollment e);
    String facePhoto(String dni);
    void deletePerson(String dni);
    List<Template> templates();
    void insertChallenge(Challenge c);
    Challenge challenge(String id);
    Challenge loginChallenge(String id);
    void consume(String id);
    Device device(String id);
    void bind(Device d);
    void unbind(String dni);
    boolean attended(String dni,LocalDate date);
    Attendance insertAttendance(Attendance a);
    List<Attendance> attendance(LocalDate date);
    List<Attendance> attendance(LocalDate date, String dni);
    List<Attendance> report(LocalDate from,LocalDate to);
    void insertTrial(FaceTrial trial);
    TrialSummary trialSummary(String galleryVersion,double threshold,String capturePolicy);
}
