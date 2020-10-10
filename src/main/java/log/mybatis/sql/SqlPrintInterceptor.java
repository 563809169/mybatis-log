package log.mybatis.sql;

import com.google.common.base.Stopwatch;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Sql执行时间记录拦截器
 * @author npj
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SqlPrintInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlPrintInterceptor.class);


    private boolean defaultPrint = true;

    @Autowired
    private SqlPrinter sqlPrinter;

    @Lazy
    @Resource
    private SqlSessionTemplate sqlSessionTemplate;

    public SqlPrintInterceptor() {
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable e) {
            doSendMailIfNecessary(invocation, ExceptionUtil.unwrapThrowable(e));
            throw e;
        }
        long executionTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

        if (log.isDebugEnabled()) {
            stopwatch = Stopwatch.createStarted();
        }
        try {
            sqlPrinter.print(invocation, executionTime, result, defaultPrint);
        } catch (Exception e) {
            log.error("打印sql异常", e);
        } finally {
            if (log.isDebugEnabled()) {
                // 有的时候sql特别长, 拼接可能会有性能问题, log下时间
                log.debug("logSql time {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
            }
        }
        return result;
    }



    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    public boolean isDefaultPrint() {
        return defaultPrint;
    }

    public void setDefaultPrint(boolean defaultPrint) {
        log.info("defaultPrintSql = {}", defaultPrint);
        this.defaultPrint = defaultPrint;
    }


    public Optional<SqlLog> getSqlLog(Invocation invocation) throws ClassNotFoundException {
        StatementHandler stmtHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaStmtHandler = SystemMetaObject.forObject(stmtHandler);
        MappedStatement mappedStatement = (MappedStatement) metaStmtHandler.getValue("delegate.mappedStatement");
        // 获取方法上注解
        String fullMapperMethod = mappedStatement.getId();
        Class<?> mapperClass = getMapperClass(fullMapperMethod);
        // 目标方法
        String targetMethodName = getMapperMethodName(fullMapperMethod);
        // 拿不到参数类型列表, 这里循环下, 正好mybatis不能重载
        return Arrays.stream(mapperClass.getMethods())
                .filter(method -> method.getName().equals(targetMethodName))
                .findFirst()
                .map(method -> method.getAnnotation(SqlLog.class));
    }

    /**
     * 发送异常邮件
     */
    private void doSendMailIfNecessary(Invocation invocation, Throwable e) {
        try {
            if (getSqlLog(invocation).map(sqlLog -> {
                // 翻译下异常
                Throwable translateException = sqlSessionTemplate.getPersistenceExceptionTranslator()
                        .translateExceptionIfPossible(new PersistenceException(e));
                if (translateException == null) {
                    translateException = e;
                }
                if (log.isDebugEnabled()) {
                    log.debug("ignoreExceptionList is {} translateException is {}", Arrays.toString(sqlLog.ignoreExceptionList()),
                            translateException.getClass().getSimpleName());
                }
                Class<? extends Exception>[] ignoreExceptionList = sqlLog.ignoreExceptionList();
                // 有一个匹配返回
                for (Class<? extends Exception> ignoreException : ignoreExceptionList) {
                    if (ignoreException.isAssignableFrom(translateException.getClass())) {
                        if (log.isDebugEnabled()) {
                            log.debug("匹配到忽略异常");
                        }
                        return false;
                    }
                }
                return true;
            }).orElse(true)) {

                // mail
            }
        } catch (Exception ex) {
            log.error("发送邮件逻辑异常", ex);
        }
    }


    private Class<?> getMapperClass(String fullMapperMethod) throws ClassNotFoundException {
        String mapperClassName = fullMapperMethod.substring(0, fullMapperMethod.lastIndexOf("."));
        return Class.forName(mapperClassName);
    }

    private String getMapperMethodName(String fullMapperMethod) {
        return fullMapperMethod.substring(fullMapperMethod.lastIndexOf(".") + 1);
    }


}