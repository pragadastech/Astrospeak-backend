package com.astrotalk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class Repos {
    private Repos() {}

    public interface UserRepo extends JpaRepository<Models.User, String> {
        Optional<Models.User> findByEmailIgnoreCase(String email);
        List<Models.User> findByRoleOrderByCreatedAtDesc(String role);
        long countByRole(String role);
    }

    public interface ProfileRepo extends JpaRepository<Models.Profile, String> {
        List<Models.Profile> findByUserIdOrderByCreatedAtAsc(String userId);
    }

    public interface PlanRepo extends JpaRepository<Models.Plan, String> {
        List<Models.Plan> findByActiveTrueOrderBySortOrderAsc();
    }

    public interface ConsultationRepo extends JpaRepository<Models.Consultation, String> {
        List<Models.Consultation> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Models.Consultation> findAllByOrderByCreatedAtDesc();
    }

    public interface ReportRepo extends JpaRepository<Models.Report, String> {
        List<Models.Report> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Models.Report> findAllByOrderByCreatedAtDesc();
    }

    public interface ReportFileRepo extends JpaRepository<Models.ReportFile, String> {}

    public interface ChatRepo extends JpaRepository<Models.ChatMessage, String> {
        List<Models.ChatMessage> findByProfileIdOrderByCreatedAtAsc(String profileId);
    }

    public interface MemoryRepo extends JpaRepository<Models.MemoryFact, String> {
        List<Models.MemoryFact> findByProfileIdOrderByCreatedAtAsc(String profileId);
    }

    public interface NotificationRepo extends JpaRepository<Models.Notification, String> {
        List<Models.Notification> findTop100ByOrderByCreatedAtDesc();
    }

    public interface SettingRepo extends JpaRepository<Models.Setting, String> {}

    public interface QuestionRepo extends JpaRepository<Models.IntakeQuestion, String> {
        List<Models.IntakeQuestion> findByActiveTrueOrderBySortOrderAsc();
        List<Models.IntakeQuestion> findAllByOrderBySortOrderAsc();
    }
}
