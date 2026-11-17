package com.astrotalk.service;

import com.astrotalk.config.AppProperties;
import com.astrotalk.domain.Models;
import com.astrotalk.domain.Repos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class NotifyService {
    private final Repos.NotificationRepo notifications;
    private final ObjectProvider<JavaMailSender> mail;
    private final AppProperties props;

    private final String smtpPassword;

    public NotifyService(Repos.NotificationRepo notifications, ObjectProvider<JavaMailSender> mail, AppProperties props,
                         @Value("${spring.mail.password:}") String smtpPassword) {
        this.notifications = notifications;
        this.mail = mail;
        this.props = props;
        this.smtpPassword = smtpPassword == null ? "" : smtpPassword;
    }

    public void welcome(Models.User user) {
        String text = "Hello " + user.fullName + ",\n\nYour AstroGreet account is ready. Sign in with this email address.\n\nWe never send a password by email. If you did not create this account, you can ignore this message.";
        dispatch(user.email, user.phone, "Your AstroGreet account is ready", text);
    }

    /** Welcome written for the landing-page start. Returns true when the email actually left the server. */
    public boolean accountCreated(Models.User user, String password) {
        String phone = user.phone == null || user.phone.isBlank() ? "—" : user.phone;
        String text = "Hello " + user.fullName + ",\n\nYour account is created.\n\nThese are your sign-in details:\nName: " + user.fullName + "\nEmail: " + user.email + "\nPhone: " + phone + "\nPassword: " + password + "\n\nSign in with this email and password for Vedic astrology, daily wishes and expert guidance.\n\nAstroGreet";
        boolean sent = email(user.email, "Your account is created — AstroGreet", text);
        if (user.phone != null && !user.phone.isBlank()) {
            whatsapp(user.phone, "Your AstroGreet account is created. We sent the sign-in details to " + user.email + ".");
        }
        return sent;
    }

    /** Staff sign-in details. The password is only in the email, never on the admin screen. */
    public boolean employeeAccess(Models.User user, String password) {
        String text = "Hello " + user.fullName + ",\n\nAn administrator created your AstroGreet staff account.\n\nSign in with:\nEmail: " + user.email + "\nPassword: " + password + "\n\nAfter you sign in, you can change this password from your profile.\n\nAstroGreet";
        return email(user.email, "Your staff sign-in — AstroGreet", text);
    }

    /** A new password for someone who used Forgot password. */
    public boolean passwordReset(Models.User user, String password) {
        String text = "Hello " + user.fullName + ",\n\nA new password was requested for your AstroGreet account.\n\nSign in with:\nEmail: " + user.email + "\nPassword: " + password + "\n\nIf you did not ask for this, sign in and change the password.\n\nAstroGreet";
        return email(user.email, "Your new password — AstroGreet", text);
    }

    public void booking(Models.User user, Models.Consultation c) {
        String when = (c.preferredDate == null ? "" : c.preferredDate) + (c.preferredTime == null ? "" : " at " + c.preferredTime);
        String text = "Hello " + user.fullName + ",\n\nWe have your request for " + c.planName + " (" + c.durationMin + " minutes, live session on Google Meet).\nPreferred time: " + when + ".\n\nNo payment has been taken yet. The meeting link will be sent after the session is confirmed.\n\nThis message does not include your password or birth details.";
        dispatch(user.email, user.phone, "Consultation request received — AstroGreet", text);
    }

    /** Tells the administrator who booked a future reading and what they answered, so the session can be connected. */
    public void adminConnected(String adminEmail, Models.User user, Models.Consultation c) {
        if (adminEmail == null || adminEmail.isBlank()) return;
        String phone = user.phone == null || user.phone.isBlank() ? "—" : user.phone;
        String when = (c.preferredDate == null ? "" : c.preferredDate) + (c.preferredTime == null ? "" : " at " + c.preferredTime);
        String text = "A future reading slot is booked. You can reach this person directly.\n\n"
                + "Name: " + user.fullName + "\n"
                + "Email: " + user.email + "\n"
                + "Phone: " + phone + "\n"
                + "Plan: " + c.planName + "\n"
                + "When: " + when + "\n\n"
                + "Their answers:\n" + c.intake + "\n\n"
                + "Open the admin portal, Consultations, and send the Google Meet link to connect the session.";
        email(adminEmail, "Future reading booked — " + user.fullName, text);
    }

    public void meeting(Models.User user, Models.Consultation c) {
        String text = "Hello " + user.fullName + ",\n\nYour " + c.planName + " session is " + c.status + ".\nMeeting link: " + (c.meetingLink == null ? "" : c.meetingLink) + "\n\nThis message does not include your password or birth details.";
        dispatch(user.email, user.phone, "Your live session — AstroGreet", text);
    }

    private void dispatch(String email, String phone, String subject, String text) {
        email(email, subject, text);
        whatsapp(phone, subject + "\n\n" + text);
    }

    private boolean email(String to, String subject, String text) {
        Models.Notification n = row("EMAIL", to, subject, text);
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || smtpPassword.isBlank()) {
            n.status = "SKIPPED";
            n.error = "SMTP password is not set, so the email was not sent.";
            notifications.save(n);
            return false;
        }
        boolean sent = false;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMailFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            sender.send(msg);
            n.status = "SENT";
            sent = true;
        } catch (Exception e) {
            n.status = "FAILED";
            n.error = e.getMessage();
        }
        notifications.save(n);
        return sent;
    }

    /** Emails the referral note. Returns true when the message left the mail server. */
    public boolean referralEmail(String to, String subject, String text) {
        return email(to, subject, text);
    }

    /** Invite with a signup link. The password is never included. Returns SENT or SKIPPED. */
    public String referralInvite(String phone, String referrerName, String guestName, String link) {
        String text = "Hello " + guestName + ",\n\n"
                + referrerName + " referred you to AstroGreet.\n\n"
                + "Open this link and create your account:\n" + link + "\n\n"
                + "This message does not include a password.";
        return whatsapp(phone, text);
    }

    private String whatsapp(String phone, String text) {
        if (phone == null || phone.isBlank()) return "SKIPPED";
        Models.Notification n = row("WHATSAPP", phone, "WhatsApp", text);
        if (props.getWhatsappToken() == null || props.getWhatsappToken().isBlank() || props.getWhatsappPhoneId() == null || props.getWhatsappPhoneId().isBlank()) {
            n.status = "SKIPPED";
            n.error = "WhatsApp is not connected yet.";
        } else {
            n.status = "SKIPPED";
            n.error = "WhatsApp token is present. Live sending is wired in the next pass so a bad template cannot fire during setup.";
        }
        notifications.save(n);
        return n.status;
    }

    private Models.Notification row(String channel, String to, String subject, String text) {
        Models.Notification n = new Models.Notification();
        n.channel = channel;
        n.toAddr = to;
        n.subject = subject;
        n.body = text;
        n.status = "QUEUED";
        return n;
    }
}
