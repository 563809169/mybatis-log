package log.mybatis.sql;

import com.aden.common.util.DateUtil;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author pengjie.nan
 * @date 2020/1/6
 */
@Slf4j
public abstract class AbstractSqlPrinter implements SqlPrinter {


    /**
     * key : statement id, value 是否打印
     */
    private final Map<String, Boolean> mappedStatementPrintSqlMap = Maps.newConcurrentMap();

    /**
     * 打印sql
     */
    @Override
    public void print(Invocation invocation, long executionTime, Object result, boolean defaultPrint) throws Exception {
        StatementHandler stmtHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaStmtHandler = SystemMetaObject.forObject(stmtHandler);
        MappedStatement mappedStatement = (MappedStatement) metaStmtHandler.getValue("delegate.mappedStatement");
        // 获取方法上注解
        String fullMapperMethod = mappedStatement.getId();

        // 没有注解, 默认不打sql, 直接返回
        // 有注解, 指定不打印, 直接返回
        if (mappedStatementPrintSqlMap.get(fullMapperMethod) == null) {
            boolean logSql = needLogSql(fullMapperMethod, defaultPrint);
            mappedStatementPrintSqlMap.put(fullMapperMethod, logSql);
        }
        int total = 0;
        if (result instanceof Collection) {
            total = ((Collection) result).size();
        } else if (result instanceof Map) {
            total = ((Map) result).size();
        } else if (result instanceof Optional) {
            total = ((Optional) result).isPresent() ? 1 : 0;
        } else {
            total = result == null ? 0 : 1;
        }

        // 方法是否要打印sql
        if (!mappedStatementPrintSqlMap.get(fullMapperMethod)) {
            notLogSql(fullMapperMethod, total, executionTime);
            return;
        }

        // 获取参数类型
        BoundSql boundSql = (BoundSql) metaStmtHandler.getValue("delegate.boundSql");
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        // 解析?对应的参数
        List<String> parameters = parseParameter(metaStmtHandler, mappedStatement, boundSql, parameterMappings);
        String sql = replacePlaceholder(parameters, boundSql.getSql());
        logSql(fullMapperMethod, total, executionTime, sql);
    }

    /**
     * 不打印sql的情况, 打印其他信息
     * @param fullMapperMethod mapper全限定名
     * @param total 返回总数
     * @param executionTime 执行时间
     */
    public abstract void notLogSql(String fullMapperMethod, int total, long executionTime);

    /**
     * 打印sql
     * @param fullMapperMethod mapper全限定名
     * @param total 返回总数
     * @param executionTime 执行时间
     * @param sql sql
     */
    public abstract void logSql(String fullMapperMethod, int total, long executionTime, String sql);


    protected String replacePlaceholder(List<String> parameters, String sql) {
        int parameterLen = parameters.stream().mapToInt(String::length).sum();
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + parameterLen - parameters.size());
        if (log.isTraceEnabled()) {
            log.trace("init sqlBuilder.capacity() is {}", sqlBuilder.capacity());
        }
        sqlBuilder.append(sql);

        int lastSqlPlaceholder = 0;
        // 第几个参数
        int paraInx = 0;

        boolean singleQuoteMatch = true;
        while (lastSqlPlaceholder < sqlBuilder.length()) {
            char c = sqlBuilder.charAt(lastSqlPlaceholder);

            // 当前位是' 且前一位不是\
            if (c == '\'' && lastSqlPlaceholder != 0 && sqlBuilder.charAt(lastSqlPlaceholder - 1) != '\\') {
                singleQuoteMatch = !singleQuoteMatch;
            }
            // 说明sql自身的字符串, 不需要替换
            if (!singleQuoteMatch) {
                lastSqlPlaceholder++;
                continue;
            }

            if (c == '?') {
                String value = parameters.get(paraInx++);
                sqlBuilder.replace(lastSqlPlaceholder, lastSqlPlaceholder + 1, value);
                lastSqlPlaceholder += value.length();
                continue;
            }

            // 去除回车空格 制表符
            if (c == '\n' || c == '\r' || c == '\t') {
                c = ' ';
                sqlBuilder.setCharAt(lastSqlPlaceholder, c);
            }
            // 如果碰到空格了, 匹配直到下一个非空格, 非回车
            if (c == ' ') {
                // 留一个空格
                int start = ++lastSqlPlaceholder;
                for (;lastSqlPlaceholder < sqlBuilder.length(); lastSqlPlaceholder++) {
                    char guessStopChar = sqlBuilder.charAt(lastSqlPlaceholder);
                    if (guessStopChar != ' ' && guessStopChar != '\n' && guessStopChar != '\r') {
                        break;
                    }
                }
                if (start != lastSqlPlaceholder) {
                    sqlBuilder.delete(start, lastSqlPlaceholder);
                    // 删掉之后回退下标
                    lastSqlPlaceholder = start;
                }
                continue;
            }

            // 正常字符
            lastSqlPlaceholder++;
        }
        if (log.isTraceEnabled()) {
            log.trace("after replace sqlBuilder.capacity() is {}", sqlBuilder.capacity());
        }
        return sqlBuilder.toString();
    }


    /**
     * 解析参数
     */
    protected List<String> parseParameter(MetaObject metaStmtHandler, MappedStatement mappedStatement, BoundSql boundSql, List<ParameterMapping> parameterMappings) {
        List<String> parameters = new ArrayList<>();
        if (parameterMappings != null) {
            Object parameterObject = metaStmtHandler.getValue("delegate.boundSql.parameterObject");
            Configuration configuration = mappedStatement.getConfiguration();
            MetaObject metaObject = parameterObject == null ? null : configuration.newMetaObject(parameterObject);
            TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
            for (ParameterMapping parameterMapping : parameterMappings) {
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    //  参数值
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    //  获取参数名称
                    if (boundSql.hasAdditionalParameter(propertyName)) {
                        // 获取参数值
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        value = null;
                    } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                        // 如果是单个值则直接赋值
                        value = parameterObject;
                    } else {
                        value = metaObject == null ? null : metaObject.getValue(propertyName);
                    }

                    if (value instanceof Number) {
                        parameters.add(String.valueOf(value));
                    } else if(value == null) {
                        parameters.add("null");
                    } else {
                        StringBuilder builder = new StringBuilder();
                        builder.append("'");
                        if (value instanceof Date) {
                            builder.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value));
                        } else if (value instanceof String) {
                            builder.append(value);
                        } else if (value instanceof LocalDateTime) {
                            builder.append(DateUtil.format(((LocalDateTime) value)));
                        } else if (value instanceof LocalDate) {
                            builder.append(DateUtil.format(((LocalDate) value)));
                        } else if (value instanceof Boolean) {
                            builder.append(value);
                        } else if(log.isDebugEnabled()) {
                            log.debug(value.getClass() + " sql打印类型不支持");
                        }
                        builder.append("'");
                        parameters.add(builder.toString());
                    }
                }
            }
        }
        return parameters;
    }

    /**
     * 获取方法或者类上的sqlLog注解, 判断是否打sql
     */
    protected boolean needLogSql(String fullMapperMethod, boolean defaultPrint) throws ClassNotFoundException {
        Class<?> mapperClass = getMapperClass(fullMapperMethod);
        // 目标方法
        String targetMethodName = getMapperMethodName(fullMapperMethod);
        // 拿不到参数类型列表, 这里循环下, 正好mybatis不能重载
        return ! Arrays.stream(mapperClass.getMethods())
                .filter(method -> method.getName().equals(targetMethodName))
                .findFirst()
                .map(method -> {
                    // 获取方法上注解
                    SqlLog sqlLog = method.getAnnotation(SqlLog.class);
                    if (sqlLog == null) {
                        sqlLog = mapperClass.getAnnotation(SqlLog.class);
                    }
                    return (sqlLog == null && !defaultPrint) || (sqlLog != null && !sqlLog.print());
                }).orElse(false);
    }

    protected String getMapperMethodName(String fullMapperMethod) {
        return fullMapperMethod.substring(fullMapperMethod.lastIndexOf(".") + 1);
    }

    protected Class<?> getMapperClass(String fullMapperMethod) throws ClassNotFoundException {
        String mapperClassName = fullMapperMethod.substring(0, fullMapperMethod.lastIndexOf("."));
        return Class.forName(mapperClassName);
    }



}
