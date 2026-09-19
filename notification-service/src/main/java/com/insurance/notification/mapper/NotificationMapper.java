package com.insurance.notification.mapper;

import com.insurance.notification.dto.NotificationResponse;
import com.insurance.notification.entity.Notification;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
