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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * 内部类用于计算Conditional注解
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
							  @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * 如果没有Conditional注解返回false，isConditionMatch将返回true，如果有Conditional注解则判断是否满足条件
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 *
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 * 注解元数据不存在或者注解元数据中不存在 Conditional 注解，返回 false
	 * <p>
	 * 阶段信息的补充，如果阶段信息为空需要做如下操作
	 * <p>
	 * 如果注解元数据的类型是 AnnotationMetadata 并且注解元数据中存在 Bean、 Component 、ComponentScan 、Import 和 ImportResource 注解将设置阶段信息为 PARSE_CONFIGURATION，表示配置解析阶段，反之则将阶段信息设置为 REGISTER_BEAN，表示 Bean 注册阶段。
	 * <p>
	 * 这两个阶段我们可以从代码中很好的得到结果。
	 * <p>
	 * doRegister 中我们传递的是 null 但是我们的注解元数据解析符合条件会被设置为 PARSE_CONFIGURATION ，当进入到 loadBeanDefinitionsForBeanMethod 方法后我们是对单个 Bean 的注册，传递的是一个明确的变量 ConfigurationPhase.REGISTER_BEAN
	 * <p>
	 * 提取注解 Conditional 中 value 的属性并将其转换成实例对象，得到当前 Conditional 中所有的 Condition 后进行排序，排序与 Ordered 有关。
	 * <p>
	 * 执行排序后的 Condition 如果满足下面条件就会返回 true
	 * <p>
	 * 条件一：requiredPhase 不存在或者 requiredPhase 的数据等于参数 phase
	 * <p>
	 * 条件二：!condition.matches(this.context, metadata) 执行结果为 true
	 * <p>
	 * <p>
	 * <p>
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 *
	 * @param metadata the meta data 注解元数据，存储了注解的数据信息
	 * @param phase    the phase of the call 配置解析阶段枚举存在两种状态：
	 *                 第一种：PARSE_CONFIGURATION 配置解析阶段
	 *                 第二种：REGISTER_BEAN Bean 注册阶段
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
		// 注解元数据不存在或者注解元数据中不包含Conditional注解
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return false;
		}

		// 配置解析阶段处理
		if (phase == null) {
			if (metadata instanceof AnnotationMetadata annotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate(annotationMetadata)) {
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			}
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}

		// 需要处理的 Condition , 数据从注解 Conditional 中来
		List<Condition> conditions = new ArrayList<>();
		// 获取注解 Conditional 的属性值
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				// 将注解中的数据转换成 Condition 接口
				// 从 class 转换成实例
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				// 插入注解列表
				conditions.add(condition);
			}
		}
		// 对 Condition 进行排序
		AnnotationAwareOrderComparator.sort(conditions);
		// 执行 Condition 得到验证结果
		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			// 如果类型是 ConfigurationCondition
			if (condition instanceof ConfigurationCondition configurationCondition) {
				requiredPhase = configurationCondition.getConfigurationPhase();
			}
			// matches 进行验证
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
		Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}


	/**
	 * ConditionContext的默认实现类
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
									@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private static ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory configurableListableBeanFactory) {
				return configurableListableBeanFactory;
			}
			if (source instanceof ConfigurableApplicationContext configurableApplicationContext) {
				return configurableApplicationContext.getBeanFactory();
			}
			return null;
		}

		private static Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable environmentCapable) {
				return environmentCapable.getEnvironment();
			}
			return new StandardEnvironment();
		}

		private static ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader resourceLoader) {
				return resourceLoader;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private static ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
													 @Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
