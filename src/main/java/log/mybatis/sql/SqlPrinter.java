package log.mybatis.sql;

import org.apache.ibatis.plugin.Invocation;

/**
 * @author pengjie.nan
 * @date 2020/1/6
 */
public interface SqlPrinter {

    /**
     * 打印sql
     * @param invocation 拦截到打的invocation
     * @param executionTime 执行时间
     * @param result 拿到的结果
     * @param defaultPrint 是否默认打印
     * @throws Exception .
     */
    void print(Invocation invocation, long executionTime, Object result, boolean defaultPrint) throws Exception;

}
