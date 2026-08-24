package com.ai.aiproject.common;

import com.ai.aiproject.enums.ResultCode;
import lombok.Data;

@Data
public class Result<T> {
    private String code;
    private String msg;
    private T data;
    public Result(){
    }
    public Result(String code,String msg,T data){
        this.code=code;
        this.msg=msg;
        this.data=data;
    }
    public static <T> Result<T> ok(){
        Result<T> result=new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        return result;
    }
    public static <T> Result<T> ok(T data){
        Result<T> result = ok();
        result.setData(data);
        return result;
    }
    public static <T> Result<T> error(){
        Result<T> result=new Result<>();
        result.setCode(ResultCode.ERROR.getCode());
        result.setMsg(ResultCode.ERROR.getMsg());
        return result;
    }
    public static <T> Result<T> error(String code,String msg,T data){
        Result<T> result=new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
