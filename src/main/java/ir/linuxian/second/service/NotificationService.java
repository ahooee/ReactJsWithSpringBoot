package ir.linuxian.second.service;

import ir.linuxian.second.entities.notification.Notification;
import ir.linuxian.second.repos.NotificationRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepo notificationRepo;

    public NotificationService(NotificationRepo notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public Notification notify(String username, String type, String subject, String preview, String link) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Notification notification = new Notification();
        notification.setUsername(username);
        notification.setType(type == null || type.isBlank() ? "GENERAL" : type.trim().toUpperCase());
        notification.setSubject(subject == null ? "" : subject.trim());
        notification.setPreview(preview == null ? null : preview.trim());
        notification.setLink(link);
        return notificationRepo.save(notification);
    }

    public List<Notification> list(String username, int limit) {
        List<Notification> all = notificationRepo.findByUsernameOrderByCreatedAtDesc(username);
        if (limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public long unreadCount(String username) {
        return notificationRepo.countByUsernameAndReadFalse(username);
    }

    @Transactional
    public Notification markRead(Long id, String username) {
        Notification notification = notificationRepo.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
        notification.setRead(true);
        return notificationRepo.save(notification);
    }

    @Transactional
    public void markAllRead(String username) {
        for (Notification n : notificationRepo.findByUsernameOrderByCreatedAtDesc(username)) {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepo.save(n);
            }
        }
    }

    @Transactional
    public void delete(Long id, String username) {
        Notification notification = notificationRepo.findByIdAndUsername(id, username)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + id));
        notificationRepo.delete(notification);
    }

    @Transactional
    public void clear(String username) {
        notificationRepo.deleteByUsername(username);
    }
}
