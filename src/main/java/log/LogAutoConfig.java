package log;

import log.mybatis.sql.DefaultSqlPrinter;
import log.mybatis.sql.SqlPrintInterceptor;
import log.mybatis.sql.SqlPrinter;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * @author pengjie.nan
 * @date 2020/1/6
 */
@Configurable
public class LogAutoConfig {


    @Bean
    @ConditionalOnBean(SqlPrintInterceptor.class)
    @ConditionalOnMissingBean(SqlPrinter.class)
    public SqlPrinter sqlPrinter() {
        return new DefaultSqlPrinter();
    }




}
