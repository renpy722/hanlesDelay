package ren.util;

import org.hibernate.validator.HibernateValidator;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class ValidationUtil {
    /**
     * 开启快速结束模式 failFast (true)
     */
    private static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 校验对象
     *
     * @param t      bean
     * @return ValidResult
     */
    public static <T> void validateBean(T t) {
        Set<ConstraintViolation<T>> violationSet = validator.validate(t);
        boolean hasError = violationSet != null && violationSet.size() > 0;
        if (hasError){
            AtomicReference<String> errorMsg = new AtomicReference<>("");
            violationSet.forEach(item -> errorMsg.set(errorMsg.get()+item.getMessage()));
            throw new ValidateException(errorMsg.get());
        }
    }
    static class ValidateException extends RuntimeException{
        public ValidateException(String message) {
            super(message);
        }
    }
}