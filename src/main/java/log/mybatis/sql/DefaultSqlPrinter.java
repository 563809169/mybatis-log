package log.mybatis.sql;

import lombok.extern.slf4j.Slf4j;

/**
 * @author pengjie.nan
 * @date 2020/1/6
 */
@Slf4j
public class DefaultSqlPrinter extends AbstractSqlPrinter {

    @Override
    public void notLogSql(String fullMapperMethod, int total, long executionTime) {
        log.info("{},total:{},{}ms", getMapperMethodName(fullMapperMethod), total, executionTime);
    }

    @Override
    public void logSql(String fullMapperMethod, int total, long executionTime, String sql) {
        log.info("{},total:{},{}ms {}", getMapperMethodName(fullMapperMethod), total, executionTime, sql);
    }
}
