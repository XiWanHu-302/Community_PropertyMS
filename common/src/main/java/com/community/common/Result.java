package com.community.common;

import java.io.Serializable;

/**
 * 统一响应体 —— 所有接口返回此格式，前端统一解析
 *
 * @param <T> 数据泛型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：200=成功，其他=失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 返回数据 */
    private T data;

    // ========== 私有构造器，通过静态方法创建 ==========

    private Result() {}

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ========== 成功响应 ==========

    /** 成功（无数据） */
    public static <T> Result<T> ok() {
        return new Result<>(200, "操作成功", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 成功（仅自定义消息，无数据） */
    public static <T> Result<T> ok(String message) {
        return new Result<>(200, message, null);
    }

    /** 成功（自定义消息 + 数据） */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ========== 失败响应 ==========

    /** 失败（自定义消息） */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /** 失败（自定义状态码 + 消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ========== getter / setter ==========

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
