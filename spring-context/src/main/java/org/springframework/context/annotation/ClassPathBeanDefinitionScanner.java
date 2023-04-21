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

package org.springframework.context.annotation;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * 注解Bean扫描器
 * https://blog.csdn.net/sermonlizhi/article/details/120611484
 * A bean definition scanner that detects bean candidates on the classpath,
 * registering corresponding bean definitions with a given registry ({@code BeanFactory}
 * or {@code ApplicationContext}).
 *
 * <p>Candidate classes are detected through configurable type filters. The
 * default filters include classes that are annotated with Spring's
 * {@link org.springframework.stereotype.Component @Component},
 * {@link org.springframework.stereotype.Repository @Repository},
 * {@link org.springframework.stereotype.Service @Service}, or
 * {@link org.springframework.stereotype.Controller @Controller} stereotype.
 *
 * <p>Also supports Jakarta EE's {@link jakarta.annotation.ManagedBean} and
 * JSR-330's {@link jakarta.inject.Named} annotations, if available.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see AnnotationConfigApplicationContext#scan
 * @see org.springframework.stereotype.Component
 * @see org.springframework.stereotype.Repository
 * @see org.springframework.stereotype.Service
 * @see org.springframework.stereotype.Controller
 * @since 2.5
 */
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {

	private final BeanDefinitionRegistry registry;

	private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

	@Nullable
	private String[] autowireCandidatePatterns;

	private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

	private boolean includeAnnotationConfig = true;


	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory.
	 *
	 * @param registry the {@code BeanFactory} to load bean definitions into, in the form
	 *                 of a {@code BeanDefinitionRegistry}
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
		this(registry, true);
	}

	/**
	 * 以AnnotationConfigApplicationContext为例,Spring容器在启动的时候,会创建BeanDefinitionScanner扫描器,在创建Scnner的时候会注册一个Component的默认包含过滤器
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory.
	 * <p>If the passed-in bean factory does not only implement the
	 * {@code BeanDefinitionRegistry} interface but also the {@code ResourceLoader}
	 * interface, it will be used as default {@code ResourceLoader} as well. This will
	 * usually be the case for {@link org.springframework.context.ApplicationContext}
	 * implementations.
	 * <p>If given a plain {@code BeanDefinitionRegistry}, the default {@code ResourceLoader}
	 * will be a {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}.
	 * <p>If the passed-in bean factory also implements {@link EnvironmentCapable} its
	 * environment will be used by this reader.  Otherwise, the reader will initialize and
	 * use a {@link org.springframework.core.env.StandardEnvironment}. All
	 * {@code ApplicationContext} implementations are {@code EnvironmentCapable}, while
	 * normal {@code BeanFactory} implementations are not.
	 *
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @see #setResourceLoader
	 * @see #setEnvironment
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
		this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
	}

	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory and
	 * using the given {@link Environment} when evaluating bean definition profile metadata.
	 * <p>If the passed-in bean factory does not only implement the {@code
	 * BeanDefinitionRegistry} interface but also the {@link ResourceLoader} interface, it
	 * will be used as default {@code ResourceLoader} as well. This will usually be the
	 * case for {@link org.springframework.context.ApplicationContext} implementations.
	 * <p>If given a plain {@code BeanDefinitionRegistry}, the default {@code ResourceLoader}
	 * will be a {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver}.
	 *
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @param environment       the Spring {@link Environment} to use when evaluating bean
	 *                          definition profile metadata
	 * @see #setResourceLoader
	 * @since 3.1
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment) {

		this(registry, useDefaultFilters, environment,
				(registry instanceof ResourceLoader resourceLoader ? resourceLoader : null));
	}

	/**
	 * Create a new {@code ClassPathBeanDefinitionScanner} for the given bean factory and
	 * using the given {@link Environment} when evaluating bean definition profile metadata.
	 *
	 * @param registry          the {@code BeanFactory} to load bean definitions into, in the form
	 *                          of a {@code BeanDefinitionRegistry}
	 * @param useDefaultFilters whether to include the default filters for the
	 *                          {@link org.springframework.stereotype.Component @Component},
	 *                          {@link org.springframework.stereotype.Repository @Repository},
	 *                          {@link org.springframework.stereotype.Service @Service}, and
	 *                          {@link org.springframework.stereotype.Controller @Controller} stereotype annotations
	 * @param environment       the Spring {@link Environment} to use when evaluating bean
	 *                          definition profile metadata
	 * @param resourceLoader    the {@link ResourceLoader} to use
	 * @since 4.3.6
	 */
	public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
										  Environment environment, @Nullable ResourceLoader resourceLoader) {

		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		this.registry = registry;

		if (useDefaultFilters) {
			registerDefaultFilters();
		}
		setEnvironment(environment);
		setResourceLoader(resourceLoader);
	}


	/**
	 * Return the BeanDefinitionRegistry that this scanner operates on.
	 */
	@Override
	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	/**
	 * Set the defaults to use for detected beans.
	 *
	 * @see BeanDefinitionDefaults
	 */
	public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
		this.beanDefinitionDefaults =
				(beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults());
	}

	/**
	 * Return the defaults to use for detected beans (never {@code null}).
	 *
	 * @since 4.1
	 */
	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		return this.beanDefinitionDefaults;
	}

	/**
	 * Set the name-matching patterns for determining autowire candidates.
	 *
	 * @param autowireCandidatePatterns the patterns to match against
	 */
	public void setAutowireCandidatePatterns(@Nullable String... autowireCandidatePatterns) {
		this.autowireCandidatePatterns = autowireCandidatePatterns;
	}

	/**
	 * Set the BeanNameGenerator to use for detected bean classes.
	 * <p>Default is a {@link AnnotationBeanNameGenerator}.
	 */
	public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator =
				(beanNameGenerator != null ? beanNameGenerator : AnnotationBeanNameGenerator.INSTANCE);
	}

	/**
	 * Set the ScopeMetadataResolver to use for detected bean classes.
	 * Note that this will override any custom "scopedProxyMode" setting.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 *
	 * @see #setScopedProxyMode
	 */
	public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}

	/**
	 * Specify the proxy behavior for non-singleton scoped beans.
	 * Note that this will override any custom "scopeMetadataResolver" setting.
	 * <p>The default is {@link ScopedProxyMode#NO}.
	 *
	 * @see #setScopeMetadataResolver
	 */
	public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
		this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
	}

	/**
	 * Specify whether to register annotation config post-processors.
	 * <p>The default is to register the post-processors. Turn this off
	 * to be able to ignore the annotations or to process them differently.
	 */
	public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
		this.includeAnnotationConfig = includeAnnotationConfig;
	}


	/**
	 * scan方法的入参是字符串数组,可以同时指定多个包进行扫描,调用doScan方法来进行扫描
	 * Perform a scan within the specified base packages.
	 *
	 * @param basePackages the packages to check for annotated classes
	 * @return number of beans registered
	 */
	public int scan(String... basePackages) {
		// 在执行扫描方法前beanDefinition的数量
		int beanCountAtScanStart = this.registry.getBeanDefinitionCount();
		// 真正的扫描方法
		doScan(basePackages);

		// 是否需要注册 注解的配置处理器
		// Register annotation config processors, if necessary.
		if (this.includeAnnotationConfig) {
			// 注册注解后置处理器
			AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
		}
		//返回此次注册的数量
		//  当前 BeanDefinition 数量 - 历史 B
		return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
	}

	/**
	 * 填充扫描生成BeanDefinition的各个属性
	 * Perform a scan within the specified base packages,
	 * returning the registered bean definitions.
	 * <p>This method does <i>not</i> register an annotation config processor
	 * but rather leaves this up to the caller.
	 *
	 * @param basePackages the packages to check for annotated classes
	 * @return set of beans registered if any for tooling registration purposes (never {@code null})
	 */
	protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		// bean 定义持有器列表
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		//遍历需要扫描的包路径
		for (String basePackage : basePackages) {
			//调用findCandidateComponents方法，生成BeanDefinition
			//扫描生成BeanDefinition
			//获取所有符合条件的BeanDefinition
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			// 循环候选bean定义
			for (BeanDefinition candidate : candidates) {
				//填充Scope属性值,如果注解Scope有值,用该值填充
				//绑定BeanDefinition与Scope
				// 获取 作用域元数据
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				// 设置作用
				candidate.setScope(scopeMetadata.getScopeName());
				//生成beanName
				//查看是否配置类是否指定bean的名称，如没指定则使用类名首字母小写
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				//设置默认值以及是否可以作为依赖注入
				//下面两个if是处理lazy、Autowire、DependencyOn、initMethod、enforceInitMethod、destroyMethod、enforceDestroyMethod、Primary、Role、Description这些逻辑的
				// 类型判断 AbstractBeanDefinition
				if (candidate instanceof AbstractBeanDefinition abstractBeanDefinition) {
					// bean 定义的后置处理
					postProcessBeanDefinition(abstractBeanDefinition, beanName);
				}
				// 类型判断 AnnotatedBeanDefinition
				if (candidate instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
					// 通用注解的处理
					// 解析@Lazy、@Primary、@DependsOn、@Role、@Description
					AnnotationConfigUtils.processCommonDefinitionAnnotations(annotatedBeanDefinition);
				}
				// 检查Spring容器中是否已经存在该beanName
				//检查bean是否存在
				// 候选检测
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					//检查scope是否创建，如未创建则进行创建
					// 作用于属性应用
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
					// 注册 --放入到registry的BeanfinitionMap中
					// 注册 bean定义
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}

	/**
	 * Apply further settings to the given bean definition,
	 * beyond the contents retrieved from scanning the component class.
	 *
	 * @param beanDefinition the scanned bean definition
	 * @param beanName       the generated bean name for the given bean
	 */
	protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
		// 设置BeanDefinition的默认值
		beanDefinition.applyDefaults(this.beanDefinitionDefaults);
		// AutowireCandidate表示某个Bean能否被用来做依赖注入
		if (this.autowireCandidatePatterns != null) {
			beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
		}
	}

	/**
	 * Register the specified bean with the given registry.
	 * <p>Can be overridden in subclasses, e.g. to adapt the registration
	 * process or to register further bean definitions for each scanned bean.
	 *
	 * @param definitionHolder the bean definition plus bean name for the bean
	 * @param registry         the BeanDefinitionRegistry to register the bean with
	 */
	protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
	}


	/**
	 * 如果BeanfinitionMap中不存在当前beanName，则返回True，生成一个新的Beanfinition注册到BeanfinitionMap
	 * <p>
	 * 如果存在beanName，则从BeanfinitionMap中根据beanName取出Beanfinition，与当前的Beanfinition比较，判断是否相等，如果相等说明同一个类被扫描了两次，无需再向BeanfinitionMap中添加，如果不相等，则直接抛出异常
	 * ————————————————
	 * 版权声明：本文为CSDN博主「sermonlizhi」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
	 * 原文链接：https://blog.csdn.net/sermonlizhi/article/details/120611484
	 * <p>
	 * Check the given candidate's bean name, determining whether the corresponding
	 * bean definition needs to be registered or conflicts with an existing definition.
	 *
	 * @param beanName       the suggested name for the bean
	 * @param beanDefinition the corresponding bean definition
	 * @return {@code true} if the bean can be registered as-is;
	 * {@code false} if it should be skipped because there is an
	 * existing, compatible bean definition for the specified name
	 * @throws ConflictingBeanDefinitionException if an existing, incompatible
	 *                                            bean definition has been found for the specified name
	 */
	protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
		// 当前注册器中是否包含 beanName
		if (!this.registry.containsBeanDefinition(beanName)) {
			return true;
		}
		// 注册器中的 beanName 的 beanInstance
		BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
		BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
		if (originatingDef != null) {
			existingDef = originatingDef;
		}
		// 是否兼容，如果兼容返回false表示不会重新注册到Spring容器中，如果不冲突则会抛异常。
		// 两个对象是否兼容
		if (isCompatible(beanDefinition, existingDef)) {
			return false;
		}
		throw new ConflictingBeanDefinitionException("Annotation-specified bean name '" + beanName +
				"' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
				"non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]");
	}

	/**
	 * Determine whether the given new bean definition is compatible with
	 * the given existing bean definition.
	 * <p>The default implementation considers them as compatible when the existing
	 * bean definition comes from the same source or from a non-scanning source.
	 *
	 * @param newDefinition      the new bean definition, originated from scanning
	 * @param existingDefinition the existing bean definition, potentially an
	 *                           explicitly defined one or a previously generated one from scanning
	 * @return whether the definitions are considered as compatible, with the
	 * new definition to be skipped in favor of the existing definition
	 */
	protected boolean isCompatible(BeanDefinition newDefinition, BeanDefinition existingDefinition) {
		return (!(existingDefinition instanceof ScannedGenericBeanDefinition) ||  // explicitly registered overriding bean
				(newDefinition.getSource() != null && newDefinition.getSource().equals(existingDefinition.getSource())) ||  // scanned same file twice
				newDefinition.equals(existingDefinition));  // scanned equivalent class twice
	}


	/**
	 * Get the Environment from the given registry if possible, otherwise return a new
	 * StandardEnvironment.
	 */
	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		if (registry instanceof EnvironmentCapable environmentCapable) {
			return environmentCapable.getEnvironment();
		}
		return new StandardEnvironment();
	}

}
