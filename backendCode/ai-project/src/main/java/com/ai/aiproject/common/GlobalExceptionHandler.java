package com.ai.aiproject.common;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/*
    全局异常处理类
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    //参数校验异常处理
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(","));
        return Result.error(ResultCode.PARAM_ERROR.getCode(),ResultCode.PARAM_ERROR.getMsg(),message);
    }
    //业务异常处理
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e){
        //存在data数据
        if(e.getData() != null){
            return Result.error(e.getCode(),e.getMessage(),e.getData());
        }
        //不存在data数据
        return Result.error(e.getCode(),e.getMessage(),null);
    }
}
