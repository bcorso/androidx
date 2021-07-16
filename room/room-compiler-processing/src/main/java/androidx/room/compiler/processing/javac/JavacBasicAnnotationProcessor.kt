/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.compiler.processing.javac

import androidx.room.compiler.processing.XBasicAnnotationProcessor
import androidx.room.compiler.processing.XElement
import androidx.room.compiler.processing.XNullability
import androidx.room.compiler.processing.XProcessingEnv
import androidx.room.compiler.processing.XProcessingStep
import androidx.room.compiler.processing.XRoundEnv
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XTypeElement
import androidx.room.compiler.processing.javac.kotlin.KmType
import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.ImmutableSetMultimap
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * Javac implementation of a [XBasicAnnotationProcessor] with built-in support for validating and
 * deferring elements via auto-common's [BasicAnnotationProcessor].
 */
abstract class JavacBasicAnnotationProcessor :
    BasicAnnotationProcessor(), XBasicAnnotationProcessor {

    private lateinit var delegatingEnv: DelegatingProcessingEnv

    final override fun steps(): Iterable<Step> {
        delegatingEnv = DelegatingProcessingEnv()
        return processingSteps().map { DelegatingStep(it) }
    }

    final override fun postRound(roundEnv: RoundEnvironment) {
        val xRound = XRoundEnv.create(delegatingEnv, roundEnv)
        postRound(delegatingEnv, xRound)
        delegatingEnv._javacEnv = null // Reset after every round to allow GC
    }

    /** A [Step] that delegates to an [XProcessingStep]. */
    private inner class DelegatingStep(val xStep: XProcessingStep) : Step {
        override fun annotations() = xStep.annotations()

        override fun process(
            elementsByAnnotation: ImmutableSetMultimap<String, Element>
        ): Set<Element> {
            val xElementsByAnnotation = mutableMapOf<String, Set<XElement>>()
            xStep.annotations().forEach { annotation ->
                xElementsByAnnotation[annotation] =
                    elementsByAnnotation[annotation].mapNotNull { element ->
                        delegatingEnv.wrapAnnotatedElement(element, annotation)
                    }.toSet()
            }
            return xStep.process(delegatingEnv, xElementsByAnnotation).map {
                (it as JavacElement).element
            }.toSet()
        }
    }

    /** A [XProcessingEnv] that delegates to a new instance of a [JavacProcessingEnv] each round. */
    private inner class DelegatingProcessingEnv: JavacProcessingEnv {
        // The state is initialized by the first caller and
        // the state is cleared at the end of each round in BasicAnnotationProcessor#postRound()
        var _javacEnv: JavacProcessingEnv? = null
        val javacEnv
            get() = _javacEnv ?: JavacProcessingEnvImpl(processingEnv).also { _javacEnv = it }

        override val backend
            get() = javacEnv.backend
        override val filer
            get() = javacEnv.filer
        override val messager
            get() = javacEnv.messager
        override val options
            get() = javacEnv.options
        override val delegate
            get() = javacEnv.delegate
        override val elementUtils
            get() = javacEnv.elementUtils
        override val typeUtils
            get() = javacEnv.typeUtils
        override fun findGeneratedAnnotation() = javacEnv.findGeneratedAnnotation()
        override fun findType(qName: String) = javacEnv.findType(qName)
        override fun findTypeElement(qName: String) = javacEnv.findTypeElement(qName)
        override fun getArrayType(type: XType) = javacEnv.getArrayType(type)
        override fun getDeclaredType(type: XTypeElement, vararg types: XType) =
            javacEnv.getDeclaredType(type, *types)
        override fun getTypeElementsFromPackage(packageName: String) =
            javacEnv.getTypeElementsFromPackage(packageName)
        override fun wrapTypeElement(element: TypeElement) = javacEnv.wrapTypeElement(element)
        override fun wrapExecutableElement(element: ExecutableElement) =
            javacEnv.wrapExecutableElement(element)
        override fun wrapVariableElement(element: VariableElement) =
            javacEnv.wrapVariableElement(element)
        override fun wrapAnnotatedElement(element: Element, annotationName: String) =
            javacEnv.wrapAnnotatedElement(element, annotationName)
        override fun <T : JavacType> wrap(
            typeMirror: TypeMirror,
            kotlinType: KmType?,
            elementNullability: XNullability
        ): T = javacEnv.wrap(typeMirror, kotlinType, elementNullability)
    }
}