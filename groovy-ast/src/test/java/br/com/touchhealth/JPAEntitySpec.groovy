package br.com.touchhealth

import org.codehaus.groovy.tools.ast.TransformTestHelper
import spock.lang.Specification

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

import static org.codehaus.groovy.control.CompilePhase.CANONICALIZATION

class JPAEntitySpec extends Specification {


    def "deve transformar Paciente, anotada com @JPAEntity"() {
        given:
        def transformation = new JPAEntityASTTransformation()
        def invoker = new TransformTestHelper(transformation, CANONICALIZATION)
        def file = new File("./src/test/java/br/com/touchhealth/Paciente.groovy")

        when:
        def compiledClass = invoker.parse(file)

        then: "deve implementar Serializable"
        Serializable.isAssignableFrom(compiledClass)


        when:
        def entityAnnotation = compiledClass.getAnnotation(Entity)

        then: "deve estar anotada com @Entity(name = 'full qualified name da classe')"
        entityAnnotation != null
        entityAnnotation.name() == Paciente.name


        when:
        def tableAnnotation = compiledClass.getAnnotation(Table)

        then: "deve estar anotada com @Table(name = {regex})"
        tableAnnotation != null
        tableAnnotation.name().matches("Paciente_\\d{6}")


        when:
        def idField = compiledClass.getDeclaredField("id")

        then: "deve ter um field id, com anotações"
        idField.type == Long
        idField.annotations*.annotationType() == [Id, GeneratedValue]


        when:
        def instance = compiledClass.newInstance()
        instance.setId(42L)

        then: "deve ter a propriedade id, com getter e setter"
        instance.getId() == 42L

        and: "deve ter implementado equals a partir do id"
        instance == [id: 42L].asType(compiledClass)
    }

}
