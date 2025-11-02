package com.resumebuddy.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@resumebuddy.cv}")
    private String fromEmail;

    /**
     * Send email to user.
     *
     * @param to      Recipient email address
     * @param subject Email subject
     * @param body    Email body (plain text)
     * @throws MailException if email sending fails
     */
    public void sendEmail(String to, String subject, String body) throws MailException {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("Email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send email to: {}. Subject: {}", to, subject, e);
            throw e;
        }
    }

    /**
     * Send email with custom from address.
     *
     * @param from    Sender email address
     * @param to      Recipient email address
     * @param subject Email subject
     * @param body    Email body (plain text)
     * @throws MailException if email sending fails
     */
    public void sendEmail(String from, String to, String subject, String body) throws MailException {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("Email sent successfully from {} to: {}", from, to);
        } catch (MailException e) {
            log.error("Failed to send email from {} to: {}. Subject: {}", from, to, subject, e);
            throw e;
        }
    }
}
