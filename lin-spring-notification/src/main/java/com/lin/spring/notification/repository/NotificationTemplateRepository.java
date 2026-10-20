package com.lin.spring.notification.repository;

import com.lin.spring.notification.entity.NotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 通知模板数据访问。
 */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, String> {
}
