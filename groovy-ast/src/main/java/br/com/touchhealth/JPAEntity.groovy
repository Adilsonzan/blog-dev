package br.com.touchhealth

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.TYPE
import static java.lang.annotation.RetentionPolicy.SOURCE

/**
 * Use em suas classes de teste:
 *
 * - adiciona {@link javax.persistence.Entity}, com name = full qualified name da classe (em vez do simple name)
 * - adiciona {@link javax.persistence.Table}, com name = simple name da classe + um número randômico de 6 dígitos
 * - implementa {@link Serializable}
 * - adiciona propriedade id, anotada no FIELD com {@link javax.persistence.Id} e {@link javax.persistence.GeneratedValue}
 *
 * @author bbviana
 */
@Retention(SOURCE)
@Target([TYPE])
@GroovyASTTransformationClass(classes = JPAEntityASTTransformation)
@interface JPAEntity {
    // vazio
}
