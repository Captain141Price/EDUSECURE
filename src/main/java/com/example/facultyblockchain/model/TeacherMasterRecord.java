package com.example.facultyblockchain.model;

import java.io.Serializable;

public class TeacherMasterRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String teacherId;
    private String name;
    private String email;
    private String department;
    private String status;
    private String specialization;
    private String teachingExperience;
    private String qualifications;
    private String dateOfJoining;
    private String dateOfAssign;
    private String researchDetails;
    private String professionalActivities;
    private String participation;
    private String projects;
    private String publications;
    private String rawBibtex;

    public TeacherMasterRecord() {
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getTeachingExperience() {
        return teachingExperience;
    }

    public void setTeachingExperience(String teachingExperience) {
        this.teachingExperience = teachingExperience;
    }

    public String getQualifications() {
        return qualifications;
    }

    public void setQualifications(String qualifications) {
        this.qualifications = qualifications;
    }

    public String getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(String dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public String getDateOfAssign() {
        return dateOfAssign;
    }

    public void setDateOfAssign(String dateOfAssign) {
        this.dateOfAssign = dateOfAssign;
    }

    public String getResearchDetails() {
        return researchDetails;
    }

    public void setResearchDetails(String researchDetails) {
        this.researchDetails = researchDetails;
    }

    public String getProfessionalActivities() {
        return professionalActivities;
    }

    public void setProfessionalActivities(String professionalActivities) {
        this.professionalActivities = professionalActivities;
    }

    public String getParticipation() {
        return participation;
    }

    public void setParticipation(String participation) {
        this.participation = participation;
    }

    public String getProjects() {
        return projects;
    }

    public void setProjects(String projects) {
        this.projects = projects;
    }

    public String getPublications() {
        return publications;
    }

    public void setPublications(String publications) {
        this.publications = publications;
    }

    public String getRawBibtex() {
        return rawBibtex;
    }

    public void setRawBibtex(String rawBibtex) {
        this.rawBibtex = rawBibtex;
    }
}
