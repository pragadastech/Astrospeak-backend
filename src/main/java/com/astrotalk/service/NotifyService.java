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
        String text = "Hello " + user.fullName + ",\n\nYour Astrology Talk account is ready. Sign in with this email address.\n\nWe never send a password by email. If you did not create this account, you can ignore this message.";
        dispatch(user.email, user.phone, "Your Astrology Talk account is ready", text);
    }

    /** Welcome written for the landing-page start. Returns true when the email actually left the server. */
    public boolean accountCreated(Models.User user, String password) {
        String phone = user.phone == null || user.phone.isBlank() ? "—" : user.phone;
        String text = "Hello " + user.fullName + ",\n\nYour account is created.\n\nThese are your sign-in details:\nName: " + user.fullName + "\nEmail: " + user.email + "\nPhone: " + phone + "\nPassword: " + password + "\n\nSign in with this email and password to open your scientific chart.\n\nAstrology Talk";
        boolean sent = email(user.email, "Your account is created — Astrology Talk", text);
        if (user.phone != null && !user.phone.isBlank()) {
            whatsapp(user.phone, "Your Astrology Talk account is created. We sent the sign-in details to " + user.email + ".");
        }
        return sent;
    }

    public void booking(Models.User user, Models.Consultation c) {
        String when = (c.preferredDate == null ? "" : c.preferredDate) + (c.preferredTime == null ? "" : " at " + c.preferredTime);
        String text = "Hello " + user.fullName + ",\n\nWe have your request for " + c.planName + " (" + c.durationMin + " minutes, live session on Google Meet).\nPreferred time: " + when + ".\n\nNo payment has been taken yet. The meeting link will be sent after the session is confirmed.\n\nThis message does not include your password or birth details.";
        dispatch(user.email, user.phone, "Consultation request received — Astrology Talk", text);
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
        dispatch(user.email, user.phone, "Your live session — Astrology Talk", text);
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
            n.error = "SMTP app password is not set yet. The mailbox will be sales@avus24.com on Microsoft 365 once the client shares the secret.";
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

    private void whatsapp(String phone, String text) {
        if (phone == null || phone.isBlank()) return;
        Models.Notification n = row("WHATSAPP", phone, "WhatsApp", text);
        if (props.getWhatsappToken() == null || props.getWhatsappToken().isBlank() || props.getWhatsappPhoneId() == null || props.getWhatsappPhoneId().isBlank()) {
            n.status = "SKIPPED";
            n.error = "WhatsApp is not connected yet.";
        } else {
            n.status = "SKIPPED";
            n.error = "WhatsApp token is present. Live sending is wired in the next pass so a bad template cannot fire during setup.";
        }
        notifications.save(n);
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
