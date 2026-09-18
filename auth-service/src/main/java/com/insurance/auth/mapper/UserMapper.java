package com.insurance.auth.mapper;

import com.insurance.auth.dto.UserResponse;
import com.insurance.auth.entity.User;
import org.mapstruct.Mapper;

/**
 * MapStruct generates the implementation at compile time (see target/generated-sources):
 * plain getters/setters, no reflection, compile-time errors when a field is renamed.
 * Entities are never returned from controllers, so lazy collections and internal columns
 * (password hash!) cannot leak.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
