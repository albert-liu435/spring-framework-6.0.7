/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.env;

/**
 * 环境，本身是一个 PropertyResolver，但是提供了 Profile 特性，即可以根据环境得到相应数据（即激活不同的 Profile，可以得到不同的属性数据，比如用于多环境场景的配置（正式机、测试机、开发机 DataSource 配置）
 * Environment 是对 JDK 环境、Servlet 环境、Spring 环境的抽象；每个环境都有自己的配置数据，如 System.getProperties()、System.getenv() 等可以拿到 JDK 环境数据；ServletContext.getInitParameter()可以拿到 Servlet 环境配置数据等等；也就是说 Spring 抽象了一个 Environment 来表示环境配置。
 * <p>
 * <p>
 * Interface representing the environment in which the current application is running.
 * Models two key aspects of the application environment: <em>profiles</em> and
 * <em>properties</em>. Methods related to property access are exposed via the
 * {@link PropertyResolver} superinterface.
 *
 * <p>A <em>profile</em> is a named, logical group of bean definitions to be registered
 * with the container only if the given profile is <em>active</em>. Beans may be assigned
 * to a profile whether defined in XML or via annotations; see the spring-beans 3.1 schema
 * or the {@link org.springframework.context.annotation.Profile @Profile} annotation for
 * syntax details. The role of the {@code Environment} object with relation to profiles is
 * in determining which profiles (if any) are currently {@linkplain #getActiveProfiles
 * active}, and which profiles (if any) should be {@linkplain #getDefaultProfiles active
 * by default}.
 *
 * <p><em>Properties</em> play an important role in almost all applications, and may
 * originate from a variety of sources: properties files, JVM system properties, system
 * environment variables, JNDI, servlet context parameters, ad-hoc Properties objects,
 * Maps, and so on. The role of the {@code Environment} object with relation to properties
 * is to provide the user with a convenient service interface for configuring property
 * sources and resolving properties from them.
 *
 * <p>Beans managed within an {@code ApplicationContext} may register to be {@link
 * org.springframework.context.EnvironmentAware EnvironmentAware} or {@code @Inject} the
 * {@code Environment} in order to query profile state or resolve properties directly.
 *
 * <p>In most cases, however, application-level beans should not need to interact with the
 * {@code Environment} directly but instead may request to have {@code ${...}} property
 * values replaced by a property placeholder configurer such as
 * {@link org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * PropertySourcesPlaceholderConfigurer}, which itself is {@code EnvironmentAware} and
 * registered by default when using {@code <context:property-placeholder/>}.
 *
 * <p>Configuration of the {@code Environment} object must be done through the
 * {@code ConfigurableEnvironment} interface, returned from all
 * {@code AbstractApplicationContext} subclass {@code getEnvironment()} methods. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples demonstrating manipulation
 * of property sources prior to application context {@code refresh()}.
 *
 * @author Chris Beams
 * @see PropertyResolver
 * @see EnvironmentCapable
 * @see ConfigurableEnvironment
 * @see AbstractEnvironment
 * @see StandardEnvironment
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#setEnvironment
 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
 * @since 3.1
 */
public interface Environment extends PropertyResolver {

	/**
	 * 这个方法用来取到 ConfigurableEnvironment.setActiveProfiles(String...) 设置的值，这个方法搭配 @Profile 注解使用
	 * 如 ：
	 * @Bean()
	 * @Profile("dev")
	 * public Student student() {
	 *  System.out.println("createStudent");
	 *    return new Student("王五", 18);
	 * }
	 *
	 * @Test
	 * public void testImport(){
	 * AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
	 * ConfigurableEnvironment environment = annotationConfigApplicationContext.getEnvironment();
	 * environment.setActiveProfiles("dev");
	 * annotationConfigApplicationContext.register(MainConfig2.class);
	 * annotationConfigApplicationContext.refresh();
	 *  String[] beanDefinitionNames = annotationConfigApplicationContext.getBeanDefinitionNames();
	 * for (String s : beanDefinitionNames) {
	 *       System.out.println(s);
	 *    }
	 *  }
	 *
	 * @Profile("dev")
	 * environment.setActiveProfiles(" dev ");
	 * 当 setActiveProfiles 里的值在 @Profile 中存在，此时就会注册这个Bean
	 * 而 getActiveProfiles 取到的也是 setActiveProfiles 中的值
	 */

	/**
	 * Return the set of profiles explicitly made active for this environment. Profiles
	 * are used for creating logical groupings of bean definitions to be registered
	 * conditionally, for example based on deployment environment. Profiles can be
	 * activated by setting {@linkplain AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 * "spring.profiles.active"} as a system property or by calling
	 * {@link ConfigurableEnvironment#setActiveProfiles(String...)}.
	 * <p>If no profiles have explicitly been specified as active, then any
	 * {@linkplain #getDefaultProfiles() default profiles} will automatically be activated.
	 *
	 * @see #getDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	String[] getActiveProfiles();

	/**
	 * 未明确设置有效配置文件时，返回默认有效的配置文件集
	 */
	/**
	 * Return the set of profiles to be active by default when no active profiles have
	 * been set explicitly.
	 *
	 * @see #getActiveProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	String[] getDefaultProfiles();

	/**
	 * 返回一个或多个给定的配置文件是否处于活动状态，或者在没有显式活动
	 * 配置文件的情况下，返回一个或多个给定的配置文件是否包含在默认配置
	 * 文件集中。 如果个人资料以“！”开头 逻辑取反，即如果给定的配置文件未
	 * 激活，则该方法将返回true。 例如，如果配置文件“ p1”处于活动状态或“
	 * p2”处于非活动状态，则env.acceptsProfiles（“ p1”，“！p2”）将返回true。
	 * 不推荐使用
	 */
	/**
	 * Return whether one or more of the given profiles is active or, in the case of no
	 * explicit active profiles, whether one or more of the given profiles is included in
	 * the set of default profiles. If a profile begins with '!' the logic is inverted,
	 * i.e. the method will return {@code true} if the given profile is <em>not</em> active.
	 * For example, {@code env.acceptsProfiles("p1", "!p2")} will return {@code true} if
	 * profile 'p1' is active or 'p2' is not active.
	 *
	 * @throws IllegalArgumentException if called with zero arguments
	 *                                  or if any profile is {@code null}, empty, or whitespace only
	 * @see #getActiveProfiles
	 * @see #getDefaultProfiles
	 * @see #acceptsProfiles(Profiles)
	 * @deprecated as of 5.1 in favor of {@link #acceptsProfiles(Profiles)}
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);

	/**
	 * Return whether the {@linkplain #getActiveProfiles() active profiles}
	 * 返回活动概要文件是否 与给定Profiles谓词匹配。，不推荐使用
	 */
	/**
	 * Return whether the {@linkplain #getActiveProfiles() active profiles}
	 * match the given {@link Profiles} predicate.
	 */
	boolean acceptsProfiles(Profiles profiles);

}
