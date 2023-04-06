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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用来指定一个bean在创建的时候是否使用延迟创建的方式，可以标注在类，方法，构造函数，创建bean的方法入参位置或者成员变量上。所谓的延迟创建，即：当Spring容器启动时，
 * 扫描到标注@Lazy注解的Bean时，并不会真正去创建和初始化，而是等到这个bean被使用的时候才去创建并完成bean的初始化。
 * Indicates whether a bean is to be lazily initialized.
 *
 * <p>May be used on any class directly or indirectly annotated with {@link
 * org.springframework.stereotype.Component @Component} or on methods annotated with
 * {@link Bean @Bean}.
 *
 * <p>If this annotation is not present on a {@code @Component} or {@code @Bean} definition,
 * eager initialization will occur. If present and set to {@code true}, the {@code @Bean} or
 * {@code @Component} will not be initialized until referenced by another bean or explicitly
 * retrieved from the enclosing {@link org.springframework.beans.factory.BeanFactory
 * BeanFactory}. If present and set to {@code false}, the bean will be instantiated on
 * startup by bean factories that perform eager initialization of singletons.
 *
 * <p>If Lazy is present on a {@link Configuration @Configuration} class, this
 * indicates that all {@code @Bean} methods within that {@code @Configuration}
 * should be lazily initialized. If {@code @Lazy} is present and {@code false} on a {@code @Bean}
 * method within a {@code @Lazy}-annotated {@code @Configuration} class, this indicates
 * overriding the 'default lazy' behavior and that the bean should be eagerly initialized.
 *
 * <p>In addition to its role for component initialization, this annotation may also be placed
 * on injection points marked with {@link org.springframework.beans.factory.annotation.Autowired}
 * or {@link jakarta.inject.Inject}: In that context, it leads to the creation of a
 * lazy-resolution proxy for all affected dependencies, as an alternative to using
 * {@link org.springframework.beans.factory.ObjectFactory} or {@link jakarta.inject.Provider}.
 * Please note that such a lazy-resolution proxy will always be injected; if the target
 * dependency does not exist, you will only be able to find out through an exception on
 * invocation. As a consequence, such an injection point results in unintuitive behavior
 * for optional dependencies. For a programmatic equivalent, allowing for lazy references
 * with more sophistication, consider {@link org.springframework.beans.factory.ObjectProvider}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @see Primary
 * @see Bean
 * @see Configuration
 * @see org.springframework.stereotype.Component
 * @since 3.0
 */
@Target({ElementType.TYPE,// 标注在类上
		ElementType.METHOD,// 标注在方法上
		ElementType.CONSTRUCTOR,// 标注在构造函数上
		ElementType.PARAMETER,// 标注在参数上
		ElementType.FIELD})// 标注在成员变量上
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lazy {

	/**
	 * 标记是否为懒加载，默认值为true。所以可以直接标注注解，不指定value属性
	 * Whether lazy initialization should occur.
	 */
	boolean value() default true;

}
