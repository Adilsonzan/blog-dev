package br.com.touchhealth

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.ast.tools.GeneralUtils.hasDeclaredMethod
import static org.codehaus.groovy.control.CompilePhase.CANONICALIZATION

/**
 * @author bbviana
 */
@GroovyASTTransformation(phase = CANONICALIZATION)
@CompileStatic
class JPAEntityASTTransformation extends AbstractASTTransformation {

    void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes == null) return

        def targetClassNode = nodes[1] as ClassNode
        def tableName = extractTableName(targetClassNode.name)

        def ast = new AstBuilder().buildFromString(CANONICALIZATION, """
            import groovy.transform.EqualsAndHashCode
            import javax.persistence.Entity
            import javax.persistence.GeneratedValue
            import javax.persistence.Id
            import javax.persistence.Table

            @Entity(name='${targetClassNode.name}')
            @Table(name='${tableName}')
            class Clazz implements Serializable {
                @Id
                @GeneratedValue
                Long id

                boolean equals(other) {
                    if (this.is(other)) return true
                    if (getClass() != other.class) return false

                    if (id != other.id) return false

                    return true
                }

                int hashCode() {
                    return (id != null ? id.hashCode() : 0)
                }
            }

        """)

        def transformClassNode = ast[1] as ClassNode

        transformClassNode.interfaces.each {ClassNode it ->
            // metodo ja verifica se a interface ja existe
            targetClassNode.addInterface(it)
        }

        transformClassNode.annotations.each {
            if (!hasAnnotation(targetClassNode, it.classNode)) {
                targetClassNode.addAnnotation(it)
            }
        }

        transformClassNode.properties.each {
            // getFied também verifica a superclass
            // getProperty e hasProperty não verificam corretamente
            if (targetClassNode.getField(it.name) == null) {
                targetClassNode.addProperty(it)
            }
        }

        transformClassNode.getMethods().each {
            if (!hasDeclaredMethod(targetClassNode, it.name, it.parameters.size())) {
                targetClassNode.addMethod(it)
            }
        }
    }

    // Table Name é composto por "${simple name da entidade}_${numero randômico de 6 digitos}
    // O número randômico serve para evitar conflitos de nomes nas tabelas para o caso de termos entidades de mesmo Nome
    private static String extractTableName(String classFullName) {
        def sixDigitsRandom = 100_000 + new Random().nextInt(900_000)
        def className = classFullName.split("\\.")[-1]
        return "${className}_${sixDigitsRandom}"
    }
}
