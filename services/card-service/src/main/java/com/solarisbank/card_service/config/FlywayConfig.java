package com.solarisbank.card_service.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Explicit Flyway wiring for Spring Boot 4.
 *
 * <p>In Spring Boot 4, JPA's {@code entityManagerFactory} is created inside
 * Tomcat's {@code DeferredServletContainerInitializers.onStartup()} path rather
 * than during the normal {@code ApplicationContext.refresh()}.  The standard
 * {@code @AutoConfigureAfter(FlywayAutoConfiguration.class)} declared by
 * {@code HibernateJpaConfiguration} is not honoured in this deferred path, so
 * Hibernate validates the schema <em>before</em> Flyway has run any migrations.
 *
 * <p>This class fixes the ordering in two complementary ways:
 * <ol>
 *   <li>A {@code BeanFactoryPostProcessor} (static — runs before any beans are
 *       instantiated) that programmatically adds {@code "flyway"} to
 *       {@code entityManagerFactory.dependsOn}, making Spring guarantee that the
 *       Flyway bean is fully initialised first.</li>
 *   <li>An explicit {@code Flyway} bean annotated with {@code @ConditionalOnMissingBean}
 *       that calls {@code migrate()} during initialisation.  This is a fallback:
 *       if Spring Boot's {@code FlywayAutoConfiguration} is active it takes
 *       precedence; if it is skipped for any reason this bean ensures migrations
 *       still run.</li>
 * </ol>
 */
@Configuration
public class FlywayConfig {

    /**
     * Force {@code entityManagerFactory} to be created after {@code flyway}.
     *
     * <p>Declared {@code static} so that Spring can instantiate this
     * {@code BeanFactoryPostProcessor} without first creating the enclosing
     * {@code FlywayConfig} bean, avoiding circular-dependency issues.
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor flywayJpaOrdering() {
        return (ConfigurableListableBeanFactory beanFactory) -> {
            // Only wire the dependency when BOTH beans will actually exist.
            // When spring.flyway.enabled=false (e.g. H2 tests), the flyway bean is absent;
            // adding a dependsOn to a non-existent bean would throw NoSuchBeanDefinitionException.
            if (!beanFactory.containsBeanDefinition("entityManagerFactory")
                    || !beanFactory.containsBeanDefinition("flyway")) {
                return;
            }
            BeanDefinition emf = beanFactory.getBeanDefinition("entityManagerFactory");
            String[] existing = emf.getDependsOn();
            if (existing != null && Arrays.asList(existing).contains("flyway")) {
                return; // dependency already present
            }
            if (existing == null) {
                emf.setDependsOn("flyway");
            } else {
                String[] updated = Arrays.copyOf(existing, existing.length + 1);
                updated[existing.length] = "flyway";
                emf.setDependsOn(updated);
            }
        };
    }

    /**
     * Fallback {@code Flyway} bean.
     *
     * <p>Activated only when Spring Boot's auto-configuration has not registered
     * a {@code Flyway} bean (e.g. because {@code FlywayAutoConfiguration} is in a
     * module that is absent from the classpath in Spring Boot 4, or because its
     * conditional checks are not satisfied).
     *
     * <p>{@code migrate()} is called eagerly during bean initialisation so that the
     * schema is always up-to-date before the {@code entityManagerFactory} — which
     * now {@code dependsOn("flyway")} — is created.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    public Flyway flyway(DataSource dataSource,
                         @Value("${spring.flyway.repair-on-migrate:true}") boolean repairOnMigrate,
                         @Value("${spring.flyway.validate-on-migrate:false}") boolean validateOnMigrate) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .validateOnMigrate(validateOnMigrate)
                .load();
        // repairOnMigrate is not a FluentConfiguration method in Flyway 10+;
        // repair() must be called as a discrete step before migrate().
        if (repairOnMigrate) {
            flyway.repair();
        }
        flyway.migrate();
        return flyway;
    }
}
