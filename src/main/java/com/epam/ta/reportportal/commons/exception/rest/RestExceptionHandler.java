/*
 * Copyright 2016 EPAM Systems
 * 
 * 
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/commons-rules
 * 
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */ 
 
package com.epam.ta.reportportal.commons.exception.rest;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Custom implementation of Spring's error handler
 * 
 * @author Andrei Varabyeu
 * 
 */
public class RestExceptionHandler extends DefaultHandlerExceptionResolver {

	/** Error Resolver */
	private ErrorResolver errorResolver;

	/** Set of converters to be able to render response */
	private List<HttpMessageConverter<?>> messageConverters;

	public void setErrorResolver(ErrorResolver errorResolver) {
		this.errorResolver = errorResolver;
	}

	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver
	 * #doResolveException(javax.servlet.http.HttpServletRequest,
	 * javax.servlet.http.HttpServletResponse, java.lang.Object,
	 * java.lang.Exception)
	 */
	@Override
	protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		LoggerFactory.getLogger(this.getClass()).error("Handled error: ", ex);
		ModelAndView defaultError = super.doResolveException(request, response, handler, ex);
		if (null != defaultError) {
			return defaultError;
		}

		return handleCustomException(request, response, ex);
	}

	protected ModelAndView handleCustomException(HttpServletRequest request, HttpServletResponse response, Exception ex) {
		ServletWebRequest webRequest = new ServletWebRequest(request, response);

		RestError error = errorResolver.resolveError(ex);
		if (error == null) {
			return null;
		}
		applyStatusIfPossible(webRequest, error.getHttpStatus());

		try {
			return handleResponseBody(error.getErrorRS(), webRequest);
		} catch (IOException e) {
			if (logger.isWarnEnabled()) {
				logger.warn("Unable to write error message", e);
			}
			return null;
		}
	}

	private void applyStatusIfPossible(ServletWebRequest webRequest, HttpStatus status) {
		if (!WebUtils.isIncludeRequest(webRequest.getRequest())) {
			webRequest.getResponse().setStatus(status.value());
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked", "resource" })
	private ModelAndView handleResponseBody(Object body, ServletWebRequest webRequest) throws HttpMessageNotWritableException, IOException {

		HttpInputMessage inputMessage = new ServletServerHttpRequest(webRequest.getRequest());

		List<MediaType> acceptedMediaTypes = inputMessage.getHeaders().getAccept();
		if (acceptedMediaTypes.isEmpty()) {
			acceptedMediaTypes = Collections.singletonList(MediaType.ALL);
		}

		MediaType.sortByQualityValue(acceptedMediaTypes);

		HttpOutputMessage outputMessage = new ServletServerHttpResponse(webRequest.getResponse());

		Class<?> bodyType = body.getClass();

		List<HttpMessageConverter<?>> converters = this.messageConverters;

		if (converters != null) {
			for (MediaType acceptedMediaType : acceptedMediaTypes) {
				for (HttpMessageConverter messageConverter : converters) {
					if (messageConverter.canWrite(bodyType, acceptedMediaType)) {
						messageConverter.write(body, acceptedMediaType, outputMessage);
						// return empty model and view to short circuit the
						// iteration and to let
						// Spring know that we've rendered the view ourselves:
						return new ModelAndView();
					}
				}
			}
		}

		if (logger.isWarnEnabled()) {
			logger.warn("Could not find HttpMessageConverter that supports return type [" + bodyType + "] and " + acceptedMediaTypes);
		}
		return null;
	}

	/**
	 * Override default behavior and handle bind exception as custom exception
	 */
	@Override
	protected ModelAndView handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {
		return handleCustomException(request, response, ex);
	}

	@Override
	protected ModelAndView handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {
		return handleCustomException(request, response, ex);
	}

	@Override
	protected ModelAndView handleMissingServletRequestPartException(MissingServletRequestPartException ex, HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {
		return handleCustomException(request, response, ex);
	}

	@Override
	protected ModelAndView handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException {
		return handleCustomException(request, response, ex);
	}
}