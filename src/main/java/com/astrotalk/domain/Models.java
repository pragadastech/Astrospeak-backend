package com.astrotalk.domain;

import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

public final class Models {
    private Models() {}

    private static final SecureRandom RND = new SecureRandom();

    public static String newId(String prefix) {
        byte[] b = new byte[8];
        RND.nextBytes(b);
        return prefix + "_" + HexFormat.of().formatHex(b);
    }

    @MappedSuperclass
    public abstract static class Base {
        @Id @Column(length = 40) public String id;
        public String createdAt;
        public String updatedAt;

        protected abstract String prefix();

        @PrePersist
        void prePersist() {
            if (id == null) id = newId(prefix());
            if (createdAt == null) createdAt = Instant.now().toString();
            updatedAt = createdAt;
        }

        @PreUpdate
        void preUpdate() { updatedAt = Instant.now().toString(); }
    }

    @Entity @Table(name = "users")
    public static class User extends Base {
        public String role;
        public String accountType;
        public String fullName;
        @Column(unique = true) public String email;
        public String phone;
        @com.fasterxml.jackson.annotation.JsonIgnore public String passwordHash;
        public String gender;
        public String billingState;
        public String language = "en";
        /** Comma-separated portal sections an employee may open. Empty means the original staff set. */
        @Column(length = 300) public String permissions;
        /** Client who referred this account, set when they sign up from that invite. */
        public String referredBy;

        protected String prefix() { return "usr"; }
    }

    @Entity @Table(name = "profiles")
    public static class Profile extends Base {
        public String userId;
        public String relation;
        public String fullName;
        public String dob;
        public String tob;
        public String gender;
        @Column(length = 400) public String city;
        public Double lat;
        public Double lon;
        public Double tz;
        public String tzName;
        /** 0 to 249 when the date of birth is not exact. Sets the ascendant sub division. */
        public Integer division;

        protected String prefix() { return "prf"; }
    }

    @Entity @Table(name = "plans")
    public static class Plan {
        @Id @Column(length = 40) public String code;
        public String name;
        @Column(length = 400) public String tagline;
        public int minutes;
        public int priceInr;
        public int priceUsd;
        public boolean recommended;
        public int sortOrder;
        @Column(length = 4000) public String features;
        @Column(length = 800) public String note;
        public boolean active = true;
        @Column(nullable = false, columnDefinition = "boolean not null default false")
        public boolean family;
    }

    @Entity @Table(name = "consultations")
    public static class Consultation extends Base {
        public String userId;
        public String planCode;
        public String planName;
        public int durationMin;
        public String preferredDate;
        public String preferredTime;
        @Column(length = 400) public String topic;
        @Column(length = 800) public String profileIds;
        public String status;
        @Column(length = 400) public String meetingLink;
        @Column(length = 1200) public String notes;
        public String currency;
        public String billingState;
        public String gstMode;
        public long baseMinor;
        public long taxMinor;
        public long totalMinor;
        public boolean demo;
        /** Answers to the admin’s future-reading questions, stored with the booking. */
        @Column(length = 4000) public String intake;
        /** Referral points applied to this session. 1 point is ₹1. */
        public Integer pointsUsed;
        /** Amount still to pay after points, in minor units. */
        public Long payableMinor;

        protected String prefix() { return "con"; }
    }

    @Entity @Table(name = "intake_questions")
    public static class IntakeQuestion extends Base {
        @Column(length = 500) public String prompt;
        public int sortOrder;
        public boolean active = true;

        protected String prefix() { return "qst"; }
    }

    @Entity @Table(name = "reports")
    public static class Report extends Base {
        public String userId;
        public String profileId;
        @Column(length = 300) public String title;
        @Column(length = 300) public String topic;
        public String status;
        public String fileName;
        public String uploadedBy;

        protected String prefix() { return "rep"; }
    }

    @Entity @Table(name = "report_files")
    public static class ReportFile {
        @Id @Column(length = 40) public String reportId;
        public String fileName;
        @Column(columnDefinition = "bytea") public byte[] data;
    }

    @Entity @Table(name = "chat_messages")
    public static class ChatMessage extends Base {
        public String userId;
        public String profileId;
        public String role;
        @Column(length = 4000) public String text;

        protected String prefix() { return "msg"; }
    }

    @Entity @Table(name = "memory_facts")
    public static class MemoryFact extends Base {
        public String userId;
        public String profileId;
        @Column(length = 300) public String fact;

        protected String prefix() { return "mem"; }
    }

    @Entity @Table(name = "notifications")
    public static class Notification extends Base {
        public String channel;
        public String toAddr;
        @Column(length = 300) public String subject;
        @Column(length = 4000) public String body;
        public String status;
        @Column(length = 800) public String error;

        protected String prefix() { return "ntf"; }
    }

    @Entity @Table(name = "referrals")
    public static class Referral extends Base {
        public String referrerId;
        public String fullName;
        @Column(length = 80) public String phone;
        /** Digits only, so the same number cannot be referred twice. */
        @Column(unique = true, length = 20) public String phoneKey;
        @Column(unique = true) public String email;
        @Column(unique = true, length = 40) public String code;
        /** invited, then joined after they create an account. */
        public String status;
        public String joinedUserId;
        @Column(length = 400) public String signupLink;
        public String whatsappStatus;
        /** The note emailed to this person. The signup link is kept at the end. */
        @Column(length = 2200) public String message;
        public String emailStatus;

        protected String prefix() { return "ref"; }
    }

    @Entity @Table(name = "point_entries")
    public static class PointEntry extends Base {
        public String userId;
        /** Positive when earned, negative when spent on a session. */
        public int points;
        public String reason;
        public String referralId;
        public String consultationId;
        @Column(length = 300) public String note;

        protected String prefix() { return "pts"; }
    }

    @Entity @Table(name = "settings")
    public static class Setting {
        @Id @Column(length = 80) public String key;
        @Column(length = 2000) public String value;
    }
}
