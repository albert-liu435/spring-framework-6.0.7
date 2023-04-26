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

package org.springframework.core.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;

/**
 * 资源接口
 * 从实际类型的底层资源（如文件或类路径资源）中抽象出来的资源描述符的接口。
 * <p>
 * 如果每个资源以物理形式存在，则可以为其打开InputStream，但只能为某些资源返回URL或File句柄。实际行为是特定于实现的。
 * <p>
 * Interface for a resource descriptor that abstracts from the actual
 * type of underlying resource, such as a file or class path resource.
 *
 * <p>An InputStream can be opened for every resource if it exists in
 * physical form, but a URL or File handle can just be returned for
 * certain resources. The actual behavior is implementation-specific.
 *
 * @author Juergen Hoeller
 * @author Arjen Poutsma
 * @see #getInputStream()
 * @see #getURL()
 * @see #getURI()
 * @see #getFile()
 * @see WritableResource
 * @see ContextResource
 * @see UrlResource
 * @see FileUrlResource
 * @see FileSystemResource
 * @see ClassPathResource
 * @see ByteArrayResource
 * @see InputStreamResource
 * @since 28.12.2003
 */
public interface Resource extends InputStreamSource {

	/**
	 * 判断资源是否存在
	 * Determine whether this resource actually exists in physical form.
	 * <p>This method performs a definitive existence check, whereas the
	 * existence of a {@code Resource} handle only guarantees a valid
	 * descriptor handle.
	 */
	boolean exists();

	/**
	 * 是否可读
	 * Indicate whether non-empty contents of this resource can be read via
	 * {@link #getInputStream()}.
	 * <p>Will be {@code true} for typical resource descriptors that exist
	 * since it strictly implies {@link #exists()} semantics as of 5.1.
	 * Note that actual content reading may still fail when attempted.
	 * However, a value of {@code false} is a definitive indication
	 * that the resource content cannot be read.
	 *
	 * @see #getInputStream()
	 * @see #exists()
	 */
	default boolean isReadable() {
		return exists();
	}

	/**
	 * 是否能打开
	 * Indicate whether this resource represents a handle with an open stream.
	 * If {@code true}, the InputStream cannot be read multiple times,
	 * and must be read and closed to avoid resource leaks.
	 * <p>Will be {@code false} for typical resource descriptors.
	 */
	default boolean isOpen() {
		return false;
	}

	/**
	 * 是否是问你件
	 * Determine whether this resource represents a file in a file system.
	 * <p>A value of {@code true} strongly suggests (but does not guarantee)
	 * that a {@link #getFile()} call will succeed.
	 * <p>This is conservatively {@code false} by default.
	 *
	 * @see #getFile()
	 * @since 5.0
	 */
	default boolean isFile() {
		return false;
	}

	/**
	 * 返回此资源的URL句柄。
	 * Return a URL handle for this resource.
	 *
	 * @throws IOException if the resource cannot be resolved as URL,
	 *                     i.e. if the resource is not available as a descriptor
	 */
	URL getURL() throws IOException;

	/**
	 * 返回此资源的URI句柄。
	 * Return a URI handle for this resource.
	 *
	 * @throws IOException if the resource cannot be resolved as URI,
	 *                     i.e. if the resource is not available as a descriptor
	 * @since 2.5
	 */
	URI getURI() throws IOException;

	/**
	 * 返回此资源的文件句柄。
	 * Return a File handle for this resource.
	 *
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 *                                       absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException                   in case of general resolution/reading failures
	 * @see #getInputStream()
	 */
	File getFile() throws IOException;

	/**
	 * Return a {@link ReadableByteChannel}.
	 * <p>It is expected that each call creates a <i>fresh</i> channel.
	 * <p>The default implementation returns {@link Channels#newChannel(InputStream)}
	 * with the result of {@link #getInputStream()}.
	 *
	 * @return the byte channel for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource doesn't exist
	 * @throws IOException                   if the content channel could not be opened
	 * @see #getInputStream()
	 * @since 5.0
	 */
	default ReadableByteChannel readableChannel() throws IOException {
		return Channels.newChannel(getInputStream());
	}

	/**
	 * 返回文件内容为byte数组
	 * Return the contents of this resource as a byte array.
	 *
	 * @return the contents of this resource as byte array
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 *                                       absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException                   in case of general resolution/reading failures
	 * @since 6.0.5
	 */
	default byte[] getContentAsByteArray() throws IOException {
		return FileCopyUtils.copyToByteArray(getInputStream());
	}

	/**
	 * 返回文件内容为字符串
	 * Returns the contents of this resource as a string, using the specified
	 * charset.
	 *
	 * @param charset the charset to use for decoding
	 * @return the contents of this resource as a {@code String}
	 * @throws java.io.FileNotFoundException if the resource cannot be resolved as
	 *                                       absolute file path, i.e. if the resource is not available in a file system
	 * @throws IOException                   in case of general resolution/reading failures
	 * @since 6.0.5
	 */
	default String getContentAsString(Charset charset) throws IOException {
		return FileCopyUtils.copyToString(new InputStreamReader(getInputStream(), charset));
	}

	/**
	 * 确定此资源的内容长度。
	 * Determine the content length for this resource.
	 *
	 * @throws IOException if the resource cannot be resolved
	 *                     (in the file system or as some other known physical resource type)
	 */
	long contentLength() throws IOException;

	/**
	 * 最后修改时间
	 * Determine the last-modified timestamp for this resource.
	 *
	 * @throws IOException if the resource cannot be resolved
	 *                     (in the file system or as some other known physical resource type)
	 */
	long lastModified() throws IOException;

	/**
	 * 创建相对于此资源的资源。
	 * Create a resource relative to this resource.
	 *
	 * @param relativePath the relative path (relative to this resource)
	 * @return the resource handle for the relative resource
	 * @throws IOException if the relative resource cannot be determined
	 */
	Resource createRelative(String relativePath) throws IOException;

	/**
	 * 返回文件名称
	 * Determine the filename for this resource &mdash; typically the last
	 * part of the path &mdash; for example, {@code "myfile.txt"}.
	 * <p>Returns {@code null} if this type of resource does not
	 * have a filename.
	 * <p>Implementations are encouraged to return the filename unencoded.
	 */
	@Nullable
	String getFilename();

	/**
	 * 返回此资源的描述
	 * Return a description for this resource,
	 * to be used for error output when working with the resource.
	 * <p>Implementations are also encouraged to return this value
	 * from their {@code toString} method.
	 *
	 * @see Object#toString()
	 */
	String getDescription();

}
