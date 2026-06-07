/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus_boot_starter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusLanguageDriverAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.SafetyEncryptProcessor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.toolkit.AES;
import org.apache.ibatis.session.ExecutorType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class Mybatis_plus_boot_starterTest {

    @Test
    void propertiesResolveMapperLocationsAndRetainFluentCustomizations() {
        MybatisPlusProperties properties = new MybatisPlusProperties()
                .setConfigLocation("classpath:/mybatis-config.xml")
                .setMapperLocations(new String[] {
                        "classpath*:com_baomidou/mybatis_plus_boot_starter/*.class",
                        "classpath*:missing-mappers/**/*.xml"
                })
                .setTypeAliasesPackage("com_baomidou.mybatis_plus_boot_starter")
                .setTypeAliasesSuperType(Account.class)
                .setCheckConfigLocation(true)
                .setExecutorType(ExecutorType.REUSE)
                .setConfiguration(new MybatisConfiguration());

        Resource[] resources = properties.resolveMapperLocations();

        assertThat(resources).extracting(Resource::getFilename).contains("Mybatis_plus_boot_starterTest.class");
        assertThat(properties.getConfigLocation()).isEqualTo("classpath:/mybatis-config.xml");
        assertThat(properties.getTypeAliasesPackage()).isEqualTo("com_baomidou.mybatis_plus_boot_starter");
        assertThat(properties.getTypeAliasesSuperType()).isEqualTo(Account.class);
        assertThat(properties.isCheckConfigLocation()).isTrue();
        assertThat(properties.getExecutorType()).isEqualTo(ExecutorType.REUSE);
        assertThat(properties.getConfiguration()).isNotNull();
        assertThat(properties.getGlobalConfig()).isNotNull();
    }

    @Test
    void safetyEncryptProcessorDecryptsOriginTrackedProperties() {
        String key = "0123456789abcdef";
        String encryptedPassword = AES.encrypt("native-friendly-secret", key);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource("commandLineArgs", "--mpw.key=" + key));
        Map<String, Object> applicationProperties = new LinkedHashMap<>();
        applicationProperties.put("app.datasource.password", OriginTrackedValue.of("mpw:" + encryptedPassword));
        applicationProperties.put("app.datasource.username", OriginTrackedValue.of("demo"));
        environment.getPropertySources().addLast(new OriginTrackedMapPropertySource("applicationConfig", applicationProperties));

        new SafetyEncryptProcessor().postProcessEnvironment(environment, new SpringApplication(Mybatis_plus_boot_starterTest.class));

        assertThat(environment.getProperty("app.datasource.password")).isEqualTo("native-friendly-secret");
        assertThat(environment.getProperty("app.datasource.username")).isEqualTo("demo");
    }

    @Test
    void springFactoriesAdvertiseStarterAutoConfigurationAndEnvironmentProcessor() {
        ClassLoader classLoader = getClass().getClassLoader();
        List<String> autoConfigurations = SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class, classLoader);
        List<String> environmentPostProcessors = SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class,
                classLoader);

        assertThat(autoConfigurations)
                .contains(MybatisPlusAutoConfiguration.class.getName(),
                        MybatisPlusLanguageDriverAutoConfiguration.class.getName());
        assertThat(environmentPostProcessors).contains(SafetyEncryptProcessor.class.getName());
    }

}

@TableName("accounts")
class Account {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String name;

    private Integer age;

    Account() {
    }

    Account(Long id, String name, Integer age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return this.age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
