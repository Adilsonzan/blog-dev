# Groovy AST Transformations

Neste post, vamos explicar como gerar código em tempo de compilação em **Groovy** usando **AST Transformations**.

Nosso objetivo será criar a anotação **@JPAEntity** para gerar alguns _boilerplates_  que sempre fazemos ao criar entidades JPA.

```groovy

@JPAEntity
class Paciente {
  String nome
}

// em tempo de compilação queremos gerar isto:

@Entity(name="br.com.touchhealth.Paciente")
@Table(name="Paciente_123456")
class Paciente implements Serializable {
  @Id
  @GeneratedValue
  Long id

  String nome

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

```

## O que são AST Transformations?
Antes de transformar código fonte em _byte codes_, o compilador Groovy nos fornece alguns _hooks_ para que possamos gerar código customizado.

Esses hooks são implementações da classe **org.codehaus.groovy.transform.ASTTransformation**.

Ao criar uma implementação, deve-se também criar a anotação que marcará os trechos de código que serão invocados na compilação.

No nosso exemplo, criaremos a classe **JPAEntityASTTransformation**, responsável por gerar o códgigo _boilerplate_, e a anotação **JPAEntity**, que usaremos para marcar as classes que queremos manipular.


## Exemplos de Transformations do próprio Groovy
O próprio Groovy já possui algumas anotações que são processadas por AST Transformations.

- **@ToString**: adiciona um método *toString()* à classe anotada
- **@EqualsAndHashCode**: adiciona *equals()* e *hashCode()*
- **@Log**: adiciona uma instância de log
- **@Immutable**: faz com que um bean seja imutável

A lista completa você pode ver no [site oficial](http://groovy-lang.org/metaprogramming.html#_available_ast_transformations).


## Criando a anotação @JPAEntity

É uma anotação como qualquer outra. A unica coisa que devemos fazer diferente é anotá-la com **@GroovyASTTransformationClass**, especificando qual classe vai processá-la.

``` groovy
// como processaremos a anotação em tempo de compilação, usamos SOURCE
@Retention(RetentionPolicy.SOURCE)
@Target([ElementType.TYPE])
@GroovyASTTransformationClass(classes = JPAEntityASTTransformation)
@interface JPAEntity {
    // vazio
}
```


## Criando JPAEntityASTTransformation

Criar uma **ASTTransformation** envolve implementar o método
`void visit(ASTNode[] nodes, SourceUnit sourceUnit)`. Você recebe a árvore de compilação **ASTNode**, que é uma representação estrutural do seu código fonte. Nosso objetivo é adicionar a ela novos nós que representam novos trechos de código.

Existem 4 formas de manipular esta árvore. Nós vamos utilizar a que considero mais simples: escrever um código fonte em uma **String**, transformá-lo em nós ASTNode e adicioná-los à arvore original.

Consulte as próprias implementações de ASTTransformation do Groovy para ver outras formas de manipulação.

#### 1. Esqueleto da classe

- Por comodidade, estendemos **AbstractASTTransformation** em vez de implementarmos diretamente ASTTransformation; ela nos fornece alguns métodos convenientes
- **@CompileStatic** melhora o desempenho do transformador na compilação
- **phase**: Na fase **CANONICALIZATION**, a árvore AST completa e está sendo pós processada. Em geral, usamos esta fase. Para mais detalhes, veja a [documentação oficial](http://groovy-lang.org/metaprogramming.html#developing-ast-xforms).

``` groovy
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@CompileStatic
class JPAEntityASTTransformation extends AbstractASTTransformation {

    void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
        if (nodes == null) return

    }
}

```

#### 2. Gerando código _boilerplate_ a partir de uma String

Aqui, escrevemos todo o código que queremos adicionar À nossa entidade anotada com **JPAEntity**.

Para isso, usamos `new AstBuilder().buildFromString()` para transformar nosso código String em uma aŕvore AST.

A classe AstBuilder possui outros métodos, como `buildFromCode()`, que em vez de uma string, recebe uma **closure**.


``` groovy
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
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

    }
}
```

#### 3. Modificando a aŕvore AST

Por fim, devemos adicionar à arvore original os nós gerados na etapa anterior.
Esta parte é bem simples e envolve apenas manipular algumas listas.

A seguir, temos o código final:

``` groovy
@GroovyASTTransformation(phase = CANONICALIZATION)
@CompileStatic
class JPAntityASTTransformation extends AbstractASTTransformation {

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

        // adiciona implements Serializable
        transformClassNode.interfaces.each {ClassNode it ->
            // metodo ja verifica se a interface existe
            targetClassNode.addInterface(it)
        }

        // adiciona as anotações @Entity e @Table
        transformClassNode.annotations.each {
            if (!hasAnnotation(targetClassNode, it.classNode)) {
                targetClassNode.addAnnotation(it)
            }
        }

        // adiciona id, com getter/setter e com as anotações @Id e @GeneratedValue
        transformClassNode.properties.each {
            // getFied também verifica a superclass
            if (targetClassNode.getField(it.name) == null) {
                targetClassNode.addProperty(it)
            }
        }

        // adiciona equals e hashCode
        transformClassNode.getMethods().each {
            if (!GeneralUtils.hasDeclaredMethod(targetClassNode, it.name, it.parameters.size())) {
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

```


#### Utilizando a Transformation

Agora basta anotarmos qualquer classe com **@JPAEntity** e ela se torna uma entidade com:

- id gerado automaticamente
- equals e hashCode implementados a partir do id
- @Entity usando full qualified name da classe
- @Table usando o nome simples da classe mais um número aleatório de 6 dígitos
- implementação de Serializable

```groovy

@JPAEntity
class Paciente {
    String nome
}
```

## Teste unitário

Ao escrever uma ASTTransformation, é importante termos uma maneira de testá-la rapidamente.
Para isso, vamos criar um teste unitário e invocar manualmente o compilador Groovy.

Para escrever o teste, usaremos o [Spock](http://spockframework.github.io/spock/docs/), uma biblioteca [BDD](https://pt.wikipedia.org/wiki/Behavior_Driven_Development). Não vamos nos aprofundar muito nela. Isso será assunto para um próximo post.

Por ora, adicione-a como dependência maven

```xml
<dependency>
	<groupId>org.spockframework</groupId>
	<artifactId>spock-core</artifactId>
	<scope>test</scope>
	<version>1.0-groovy-2.4</version>
</dependency>
```

O modelo BDD não usa **Teste** como terminologia. Prefere o termo **Specification**. No fundo é a mesma coisa.

Nossa especificação (ou teste) fica assim:

```groovy
class JPAEntitySpec extends Specification {


    def "deve transformar Paciente, anotada com @JPAEntity"() {
        given:
        def transformation = new JPAEntityASTTransformation()
        def invoker = new TransformTestHelper(transformation, CANONICALIZATION)
        def file = new File("./src/test/java/br/com/touchhealth/Paciente.groovy")

        when:
        // invocamos o compilador groovy manualmente
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
        tableAnnotation.name().matches("Paciente\\d{6}")


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
```

# Conclusão

AST Transformations são uma forma relativamente simples e poderosa de deixar nosso código Groovy mais limpo e fácil de entender. Permite-nos também reaproveitar código de maneira transparente e flexível.

É preciso sempre tomar cuidado para não criarmos _caixas mágicas_, que geram muitos códigos escondidos que o desenvolvedor não vê.

Aqui na Touch temos usado **ASTTransformations** para diminuir o _boilerplate_ na geração de testes unitários.
O exemplo deste artigo foi retirado de uma de nossas bibliotecas internas.

O código fonte pode ser acessado no nosso [GitHub](https://github.com/touchhealth/blog-dev/tree/master/groovy-ast).
