package log.mybatis.sql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author pengjie.nan
 * @date 2019-04-13
 */
@Slf4j
public class SqlConfig implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        log.info("Enable Sql Print");
        AnnotationAttributes sqlPrint = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(EnableSqlPrint.class.getName()));
        if (sqlPrint == null) {
            return;
        }
        boolean defaultPrintSql = sqlPrint.getBoolean("defaultPrint");
        BeanDefinitionBuilder sqlPrintBeanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SqlPrintInterceptor.class);
        sqlPrintBeanDefinition.addPropertyValue("defaultPrint", defaultPrintSql);
        registry.registerBeanDefinition(SqlPrintInterceptor.class.getName(), sqlPrintBeanDefinition.getBeanDefinition());

    }
}
