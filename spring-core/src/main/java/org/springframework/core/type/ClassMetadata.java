/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.type;

import org.springframework.lang.Nullable;

/**
 * Interface that defines abstract metadata of a specific class,
 * in a form that does not require that class to be loaded yet.
 *
 * @author Juergen Hoeller
 * @see StandardClassMetadata
 * @see org.springframework.core.type.classreading.MetadataReader#getClassMetadata()
 * @see AnnotationMetadata
 * @since 2.5
 */
public interface ClassMetadata {

	/**
	 * 类名
	 * Return the name of the underlying class.
	 */
	String getClassName();

	/**
	 * 是否是接口
	 * Return whether the underlying class represents an interface.
	 */
	boolean isInterface();

	/**
	 * 是否是注解
	 * Return whether the underlying class represents an annotation.
	 *
	 * @since 4.1
	 */
	boolean isAnnotation();

	/**
	 * 是否是超类
	 * Return whether the underlying class is marked as abstract.
	 */
	boolean isAbstract();

	/**
	 * 是否允许创建,实例化
	 * Return whether the underlying class represents a concrete class,
	 * i.e. neither an interface nor an abstract class.
	 */
	default boolean isConcrete() {
		return !(isInterface() || isAbstract());
	}

	/**
	 * 是否有final修饰
	 * Return whether the underlying class is marked as 'final'.
	 */
	boolean isFinal();

	/**
	 * 是否独立
	 * 1. 不是内部类
	 * 2. 不是继承类
	 * Determine whether the underlying class is independent, i.e. whether
	 * it is a top-level class or a nested class (static inner class) that
	 * can be constructed independently of an enclosing class.
	 */
	boolean isIndependent();

	/**
	 * Return whether the underlying class is declared within an enclosing
	 * class (i.e. the underlying class is an inner/nested class or a
	 * local class within a method).
	 * <p>If this method returns {@code false}, then the underlying
	 * class is a top-level class.
	 */
	default boolean hasEnclosingClass() {
		return (getEnclosingClassName() != null);
	}

	/**
	 * Return the name of the enclosing class of the underlying class,
	 * or {@code null} if the underlying class is a top-level class.
	 */
	@Nullable
	String getEnclosingClassName();

	/**
	 * 是否有父类
	 * Return whether the underlying class has a superclass.
	 */
	default boolean hasSuperClass() {
		return (getSuperClassName() != null);
	}

	/**
	 * 父类名称
	 * Return the name of the superclass of the underlying class,
	 * or {@code null} if there is no superclass defined.
	 */
	@Nullable
	String getSuperClassName();

	/**
	 * 实现接口列表
	 * Return the names of all interfaces that the underlying class
	 * implements, or an empty array if there are none.
	 */
	String[] getInterfaceNames();

	/**
	 * 成员列表
	 * Return the names of all classes declared as members of the class represented by
	 * this ClassMetadata object. This includes public, protected, default (package)
	 * access, and private classes and interfaces declared by the class, but excludes
	 * inherited classes and interfaces. An empty array is returned if no member classes
	 * or interfaces exist.
	 *
	 * @since 3.1
	 */
	String[] getMemberClassNames();

}
