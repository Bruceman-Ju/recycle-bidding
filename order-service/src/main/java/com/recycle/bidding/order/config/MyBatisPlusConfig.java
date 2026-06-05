package com.recycle.bidding.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 *
 * 注册 OptimisticLockerInnerInterceptor 后，@Version 注解才会生效：
 * 1. SQLProvider 在生成 updateById 的 MappedStatement 时检测到 @Version
 * 2. 自动在 WHERE 注入 version = #{et.MP_OPTLOCK_VERSION_ORIGINAL}
 * 3. 该拦截器负责在运行时注入 entity 的原始 version 值
 * 4. 同时将 SET version = #{et.version} 改写为 SET version = version + 1
 *
 * 没有此拦截器时，MP 生成的 SQL 模板中包含未经初始化的 MP_OPTLOCK_VERSION_ORIGINAL 参数，
 * MyBatis 在绑定参数时找不到该参数 → Parameter 'MP_OPTLOCK_VERSION_ORIGINAL' not found
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁插件：处理 @Version 注解，注入版本号参数并改写 SET version = version + 1
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
