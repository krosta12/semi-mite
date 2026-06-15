package org.example.config;

import org.example.compiler.CppCompiler;
import org.example.engine.CppEngine;
import org.example.engine.DefaultCppEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(KebabProperties.class)
public class KebabAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CppCompiler cppCompiler(KebabProperties props) {
        return new CppCompiler(props.getCacheDir());
    }

    @Bean
    @ConditionalOnMissingBean
    public CppEngine cppEngine(CppCompiler compiler) {
        return new DefaultCppEngine(compiler);
    }
}