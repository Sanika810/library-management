package com.library.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(BusinessException e) {
        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(e.getStatus());
        mv.addObject("status", e.getStatus().value());
        mv.addObject("message", e.getMessage());
        return mv;
    }
}
