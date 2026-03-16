package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private Integer totalMessages;

    private ApiResponse(boolean success, String message, T data, Integer totalMessages) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.totalMessages = totalMessages;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data, int totalMessages) {
        return new ApiResponse<>(true, message, data, totalMessages);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public Integer getTotalMessages() { return totalMessages; }
}
