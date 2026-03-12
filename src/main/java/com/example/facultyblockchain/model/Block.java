package com.example.facultyblockchain.model;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Date;

public class Block implements Serializable {
    private static final long serialVersionUID = 1L;

    private String hash;
    private String previousHash;

    // Basic Faculty Info
    private String name;
    private String email;
    private String department;
    private String dateOfJoining;
    private String dateOfAssign;
    private String specialization;
    private String teachingExp;
    private String qualifications;

    // Research & Publications (from BibTeX)
    private String publications; // formatted BibTeX output
    private String rawBibtex;    // newly added raw BibTeX string
    private String research;
    private String publicationTitle;
    private String publicationAuthors;
    private String journal;
    private String volume;
    private String number;
    private String pages;
    private String year;
    private String publisher;
    private String organization;
    private String booktitle;

    // Other Faculty Activities
    private String professionalActivity;
    private String facultyParticipation;
    private String projects;

    private long timestamp;
    private int nonce;

    // Full Constructor
    public Block(String name, String email, String department,
                 String dateOfJoining, String dateOfAssign,
                 String specialization, String teachingExp, String qualifications,
                 String publications, String rawBibtex, String research,
                 String publicationTitle, String publicationAuthors, String journal,
                 String volume, String number, String pages, String year,
                 String publisher, String organization, String booktitle,
                 String professionalActivity, String facultyParticipation, String projects,
                 String previousHash) {

        this.name = name;
        this.email = email;
        this.department = department;
        this.dateOfJoining = dateOfJoining;
        this.dateOfAssign = dateOfAssign;
        this.specialization = specialization;
        this.teachingExp = teachingExp;
        this.qualifications = qualifications;

        this.publications = publications; // formatted output
        this.rawBibtex = rawBibtex;       // raw BibTeX string
        this.research = research;
        this.publicationTitle = publicationTitle;
        this.publicationAuthors = publicationAuthors;
        this.journal = journal;
        this.volume = volume;
        this.number = number;
        this.pages = pages;
        this.year = year;
        this.publisher = publisher;
        this.organization = organization;
        this.booktitle = booktitle;

        this.professionalActivity = professionalActivity;
        this.facultyParticipation = facultyParticipation;
        this.projects = projects;

        this.previousHash = previousHash;
        this.timestamp = new Date().getTime();
        this.hash = calculateHash();
    }

    // Simplified constructor for backward compatibility
    public Block(String name, String email, String department, String dateOfJoining,
                 String publications, String rawBibtex, String research, String professionalActivity,
                 String projects, String previousHash) {
        this(name, email, department, dateOfJoining, "", "", "", "",
             publications, rawBibtex, research, "", "", "", "", "", "", "", "",
             "", "", professionalActivity, "", projects, previousHash);
    }

    // Hash Calculation
    public String calculateHash() {
        String dataToHash =
                previousHash + name + email + department +
                dateOfJoining + dateOfAssign + specialization + teachingExp +
                qualifications + publications + rawBibtex + research + publicationTitle +
                publicationAuthors + journal + volume + number + pages + year +
                publisher + organization + booktitle +
                professionalActivity + facultyParticipation + projects +
                timestamp + nonce;

        return applySha256(dataToHash);
    }

    // Mining
    public void mineBlock(int difficulty) {
        String target = new String(new char[difficulty]).replace('\0', '0');
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    // SHA-256
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Getters
    public String getHash() { return hash; }
    public String getPreviousHash() { return previousHash; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getDateOfJoining() { return dateOfJoining; }
    public String getDateOfAssign() { return dateOfAssign; }
    public String getSpecialization() { return specialization; }
    public String getTeachingExp() { return teachingExp; }
    public String getQualifications() { return qualifications; }
    public String getPublications() { return publications; } // formatted
    public String getRawBibtex() { return rawBibtex; }        // raw
    public String getResearch() { return research; }
    public String getPublicationTitle() { return publicationTitle; }
    public String getPublicationAuthors() { return publicationAuthors; }
    public String getJournal() { return journal; }
    public String getVolume() { return volume; }
    public String getNumber() { return number; }
    public String getPages() { return pages; }
    public String getYear() { return year; }
    public String getPublisher() { return publisher; }
    public String getOrganization() { return organization; }
    public String getBooktitle() { return booktitle; }
    public String getProfessionalActivity() { return professionalActivity; }
    public String getFacultyParticipation() { return facultyParticipation; }
    public String getProjects() { return projects; }
}
