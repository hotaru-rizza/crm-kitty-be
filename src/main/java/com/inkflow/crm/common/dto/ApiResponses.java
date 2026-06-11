package com.inkflow.crm.common.dto;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    public static ResponseEntity<ApiResponse<Void>> empty() {
        return ResponseEntity.ok(ApiResponse.empty());
    }

    public static <T> ResponseEntity<ApiResponse<List<T>>> page(Page<T> page) {
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PaginationDto.from(page)));
    }

    public static ConsumerUser requireConsumer(ConsumerUser user) {
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
