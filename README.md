Let's write this service using Kotlin.

We should choose one of the build systems - Gradle or Maven.

As far as we need HTTP, we can use simple and extendable framework - [Micronaut](https://micronaut.io/) or [Ktor](https://ktor.io/).
Ktor is more native to Kotlin and supports many of its features, including asynchronous execution. 

For visibility, we need logging library. We can choose one of the facades
([kotlin-logging](https://github.com/oshai/kotlin-logging), [slf4j](https://www.slf4j.org/)) with implementation
([logback](http://logback.qos.ch/), [log4j2](https://logging.apache.org/log4j/2.x/))
or directly use logging lib like [log4j2](https://logging.apache.org/log4j/2.x/).

We need to choose a way to handle JSON RPC protocol. We have several options:
1) Find existing solution and integrate it to our code.
<br>_Pros_: proper implementation will be tested and have low cost to start using it.
<br>_Cons_: there are a few options for Java and literally nothing for Kotlin. Each library bring unnecessary dependencies
like Spring and not always have proper API. No support for async execution.
2) Implement simple solution with hardcode. Just implement JSON RPC protocol to support only our needs.
<br>_Pros_: Considerably fast to implement simple controllers. Don't need to test any option for lib.
<br>_Cons_: Hard to extend. Hard to reuse.
3) Implement own parser for annotated classes/interfaces. This parser will use reflection to build abstract 
representation of a JSON RPC prepared service. This representation can be called abstractly and also can be integrated
to existing frameworks.
<br>_Pros_: Can be reused. Can be easily extended.
<br>_Cons_: Hard to implement. Require thorough testing.

In case if we write our own JSON RPC protocol parser, we can put it to separate module and use it in other projects.

Draft API (it terms of gRPC for simplicity): [API](api-example.proto)

For file operations, Kotlin's standard library should be enough.

We should test our code. JUnit 5 and mocks should also be enough.

Also, we should add Dockerfile with some default JVM parameters.

In Kubernetes yaml we can add additional JVM parameters, such as **Xmx**, because it is directly related to environment
where our service will be executed.

Some generic Kubernetes parameters, such as namespace, can be moved to Helm chart parameters.
