package org.scse.cloudagent.controller;

import jakarta.validation.ConstraintViolationException;
import org.scse.cloudagent.domain.AgentErrorCode;
import org.scse.cloudagent.dto.ApiError;
import org.scse.cloudagent.service.AgentResourceNotFoundException;
import org.scse.cloudagent.state.InvalidStateTransitionException;
import org.scse.cloudagent.tool.InvalidToolArgumentsException;
import org.scse.cloudagent.tool.UnknownToolException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AgentExceptionHandler {
    @ExceptionHandler(AgentResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(AgentResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, AgentErrorCode.RUN_NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            InvalidToolArgumentsException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, AgentErrorCode.INVALID_TOOL_ARGUMENTS, exception.getMessage());
    }

    @ExceptionHandler(UnknownToolException.class)
    ResponseEntity<ApiError> unknownTool(UnknownToolException exception) {
        return error(HttpStatus.BAD_REQUEST, AgentErrorCode.UNKNOWN_TOOL, exception.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ApiError> invalidState(InvalidStateTransitionException exception) {
        return error(HttpStatus.CONFLICT, AgentErrorCode.INVALID_STATE_TRANSITION, exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, AgentErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, MDC.get("traceId"), Instant.now()));
    }
}
