package log.mybatis.sql;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author pengjie.nan
 * @date 2019-08-02
 * 打印sql
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlLog {

    /**
     * 是否打印sql
     */
    boolean print() default true;

    /**
     * 异常屏蔽
     */
    Class<? extends Exception>[] ignoreExceptionList() default {};

}
