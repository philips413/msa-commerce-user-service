package com.devbook.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 예상한 실패는 컨트롤러가 반환값으로 처리한다. 여기는 프레임워크 검증 오류와 예상 못 한 오류만 받는다. */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "요청 값을 확인해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        // 없는 경로(404), 허용 안 된 메서드(405) 등 프레임워크가 상태 코드를 정한 예외는 그 코드를 유지한다
        if (e instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            String code = status.value() == 404 ? "NOT_FOUND" : "REQUEST_REJECTED";
            return ResponseEntity.status(status).body(new ErrorResponse(code, "요청을 처리할 수 없습니다."));
        }
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "일시적인 오류가 발생했습니다."));
    }
}
