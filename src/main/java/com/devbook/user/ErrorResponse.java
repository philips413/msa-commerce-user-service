package com.devbook.user;

/** 모든 실패 응답의 형태. 계약: docs/api-contract.md */
public record ErrorResponse(String code, String message) {}
