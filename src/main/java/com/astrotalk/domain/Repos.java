package com.astrotalk.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class Repos {
    private Repos() {}

    public interface UserRepo extends JpaRepository<Models.User, String> {
        Optional<Models.User> findByEmailIgnoreCase(String email);
        List<Models.User> findByRoleOrderByCreatedAtDesc(String role);
        List<Models.User> findByRoleAndAssignedToOrderByCreatedAtDesc(String role, String assignedTo);
        long countByRole(String role);
        long countByRoleAndLeadStatus(String role, String leadStatus);
        long countByRoleAndAssignedTo(String role, String assignedTo);
        long countByRoleAndAssignedToAndLeadStatus(String role, String assignedTo, String leadStatus);
    }

    public interface ProfileRepo extends JpaRepository<Models.Profile, String> {
        List<Models.Profile> findByUserIdOrderByCreatedAtAsc(String userId);
    }

    public interface PlanRepo extends JpaRepository<Models.Plan, String> {
        List<Models.Plan> findByActiveTrueOrderBySortOrderAsc();
    }

    public interface ConsultationRepo extends JpaRepository<Models.Consultation, String> {
        List<Models.Consultation> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Models.Consultation> findByUserIdInOrderByCreatedAtDesc(List<String> userIds);
        List<Models.Consultation> findAllByOrderByCreatedAtDesc();
    }

    public interface ReportRepo extends JpaRepository<Models.Report, String> {
        List<Models.Report> findByUserIdOrderByCreatedAtDesc(String userId);
        List<Models.Report> findByUserIdInOrderByCreatedAtDesc(List<String> userIds);
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

    public interface ReferralRepo extends JpaRepository<Models.Referral, String> {
        Optional<Models.Referral> findByCode(String code);
        Optional<Models.Referral> findByEmailIgnoreCase(String email);
        Optional<Models.Referral> findByPhoneKey(String phoneKey);
        Optional<Models.Referral> findByJoinedUserId(String joinedUserId);
        List<Models.Referral> findByReferrerIdOrderByCreatedAtDesc(String referrerId);
    }

    public interface PointRepo extends JpaRepository<Models.PointEntry, String> {
        List<Models.PointEntry> findByUserIdOrderByCreatedAtDesc(String userId);
    }

    public interface QuestionRepo extends JpaRepository<Models.IntakeQuestion, String> {
        List<Models.IntakeQuestion> findByActiveTrueOrderBySortOrderAsc();
        List<Models.IntakeQuestion> findAllByOrderBySortOrderAsc();
    }

    public interface CommissionRateRepo extends JpaRepository<Models.CommissionRate, String> {
        Optional<Models.CommissionRate> findBySourceIgnoreCase(String source);
    }

    public interface CommissionRecordRepo extends JpaRepository<Models.CommissionRecord, String> {
        List<Models.CommissionRecord> findAllByOrderByCreatedAtDesc();
        List<Models.CommissionRecord> findBySourceOrderByCreatedAtDesc(String source);
        List<Models.CommissionRecord> findByMonthKeyOrderByCreatedAtDesc(String monthKey);
        List<Models.CommissionRecord> findByAssignedEmployeeIdOrderByCreatedAtDesc(String assignedEmployeeId);
        List<Models.CommissionRecord> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    }
}
