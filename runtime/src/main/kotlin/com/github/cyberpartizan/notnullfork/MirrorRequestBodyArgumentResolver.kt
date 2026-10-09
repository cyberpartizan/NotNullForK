package com.github.cyberpartizan.notnullfork

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Conventions
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import org.springframework.validation.annotation.ValidationAnnotationUtils
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.io.IOException
import java.io.PushbackInputStream

/**
 * Resolves a `@RequestBody` parameter whose Request Body has a Mirror: reads the JSON into the Mirror
 * with the ObjectMapper of the app's Jackson [converter], checks the Required Fields with
 * [requiredFieldValidator] (whether or not the parameter has `@Valid`), and maps the Mirror to the
 * Request Body. A Request Body with `@Valid` or `@Validated` is then validated as Spring would.
 *
 * Unlike Spring's own resolver, `RequestBodyAdvice` (and so `@JsonView`) is not applied.
 */
internal class MirrorRequestBodyArgumentResolver(
    private val converter: MappingJackson2HttpMessageConverter,
    private val requiredFieldValidator: Validator,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(RequestBody::class.java) &&
            Mirrors.mirrorClassFor(parameter.nestedParameterType) != null

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val mirrorClass = Mirrors.mirrorClassFor(parameter.nestedParameterType)!!
        val inputMessage = ServletServerHttpRequest(webRequest.getNativeRequest(HttpServletRequest::class.java)!!)
        val mirror = read(inputMessage, mirrorClass) ?: return missingBody(parameter, inputMessage)
        val name = Conventions.getVariableNameForParameter(parameter)

        checkRequiredFields(mirror, name, parameter)
        val requestBody = mirror.toRequestBody()
        validateIfApplicable(requestBody, name, parameter, mavContainer, webRequest, binderFactory)
        return requestBody
    }

    /** Reads the body into a Mirror, or returns `null` if the body is empty. */
    private fun read(inputMessage: HttpInputMessage, mirrorClass: Class<out Mirror<*>>): Mirror<*>? {
        val contentType = inputMessage.headers.contentType ?: MediaType.APPLICATION_OCTET_STREAM
        if (!converter.canRead(mirrorClass, contentType)) {
            throw HttpMediaTypeNotSupportedException(contentType, converter.supportedMediaTypes)
        }
        return try {
            val body = PushbackInputStream(inputMessage.body)
            val first = body.read()
            if (first == -1) {
                null
            } else {
                body.unread(first)
                converter.objectMapper.readValue(body, mirrorClass)
            }
        } catch (e: JsonProcessingException) {
            throw HttpMessageNotReadableException("JSON parse error: ${e.originalMessage}", e, inputMessage)
        } catch (e: IOException) {
            throw HttpMessageNotReadableException("I/O error while reading input message", e, inputMessage)
        }
    }

    private fun missingBody(parameter: MethodParameter, inputMessage: HttpInputMessage): Any? {
        if (parameter.getParameterAnnotation(RequestBody::class.java)!!.required) {
            throw HttpMessageNotReadableException(
                "Required request body is missing: ${parameter.executable.toGenericString()}",
                inputMessage,
            )
        }
        return null
    }

    private fun checkRequiredFields(mirror: Mirror<*>, name: String, parameter: MethodParameter) {
        val result = BeanPropertyBindingResult(mirror, name)
        requiredFieldValidator.validate(mirror, result)
        if (result.hasErrors()) throw MethodArgumentNotValidException(parameter, result)
    }

    // Same as Spring's AbstractMessageConverterMethodArgumentResolver.validateIfApplicable.
    private fun validateIfApplicable(
        requestBody: Any,
        name: String,
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ) {
        if (binderFactory == null) return
        val binder = binderFactory.createBinder(webRequest, requestBody, name)
        val hints = parameter.parameterAnnotations.firstNotNullOfOrNull {
            ValidationAnnotationUtils.determineValidationHints(it)
        }
        if (hints != null) {
            binder.validate(*hints)
            if (binder.bindingResult.hasErrors() && isBindExceptionRequired(parameter)) {
                throw MethodArgumentNotValidException(parameter, binder.bindingResult)
            }
        }
        mavContainer?.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.bindingResult)
    }

    private fun isBindExceptionRequired(parameter: MethodParameter): Boolean {
        val parameterTypes = parameter.executable.parameterTypes
        val next = parameter.parameterIndex + 1
        return next >= parameterTypes.size || !Errors::class.java.isAssignableFrom(parameterTypes[next])
    }
}
