package log.mybatis.sql;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author pengjie.nan
 * @date 2019-04-26
 * 使用sql打印组件
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SqlConfig.class)
public @interface EnableSqlPrint {

    /**
     * 是否默认打sql, 可以通过和SqlLog注解组合打印sql
     * sqlLog具有最高优先级
     */
    boolean defaultPrint() default true;

}
