package com.example.facultyblockchain.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public int sendOTP(String email) {
        int otp = new Random().nextInt(900000) + 100000; // 6-digit OTP
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Your OTP for Faculty Blockchain");
            message.setText("Your OTP is: " + otp + "\nIt expires in 5 minutes.");
            mailSender.send(message);
            System.out.println("OTP sent to: " + email);
        } catch (Exception e) {
            System.err.println("Failed to send email to " + email + ": " + e.getMessage());
        }
        return otp;
    }
}
