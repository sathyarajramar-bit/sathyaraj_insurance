package com.insurance.document.mapper;

import com.insurance.document.dto.DocumentResponse;
import com.insurance.document.entity.Document;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface DocumentMapper {

    DocumentResponse toResponse(Document document);
}
