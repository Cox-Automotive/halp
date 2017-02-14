Halp: unit test your code's dependencies
========================================

Halp is a unit testing library designed to define and enforce dependencies
between the logical components of your application. 

What Does it Do?
----------------

#### 1) Document Design

Clearly document design goals of a project through unit tests.

#### 2) Enforce Good Code Behavior

Prevent cyclic dependencies, guard contracts, and explicitly isolate contract
implementations. Detect when code exists that is not governed by a module
definition, so you don't get features slowly creeping in that are not following
the module guidelines set forth for the project.

#### 3) Simulate independent compilation units in Maven without the overhead

Don't worry about making loads of maven submodules and dependencies just to
keep your classpath clean. Avoid the xml and write unit tests instead!

Overview
--------

This project provides a simple way to define and enforce dependencies between
modules in a monolithic Java application via unit tests.  No build-time
plugins, fancy frameworks, or special classpaths are required; if your build
system can run unit tests, you can easily use `halp`. Though no
special changes are required, you may find that use of this project makes you
more intentional about your package structure. (That's a good thing.)

Often in new java projects modularity is not up-front concern. Engineers
typically start discussing the use of modules in developing systems too
late to avoid the headaches of spahghetti code and balls of mud. This
project is designed to make module definitions so painless in a small
project that there's really no excuse to avoid using it up-front.

Getting Started
---------------

Imagine an application called `blitzen` with a package structure like so:

```
blizen.ports.gateway
blizen.ports.db
blizen.ports.api

blitzen.core

blitzen.app
```

In this app, `blitzen.ports` contains all the contracts for its module
boundaries. This package depends on nothing else in the project.  The package
`blitzen.core` contains general application functionality. It uses the
interfaces defined in `ports` to do its job. You can think of all of its inputs
and outputs as having been mocked out by ports. The `app` package contains the
basic configuration and wiring code to connect the ports implementations to the
core of the application. This is where the `main` for the application would
live.  This is a reasonable modular structure for an app to maintain.

The last part of this structure is the specific implementations of the various
ports. Let's assume that we'll nest specific implementations of ports underneath
their contract definitions for now:

```
blizen.ports.gateway.http
blizen.ports.db.mysql
blizen.ports.api.thrift
```

At this point we have a fairly complete application. It's able to receive
information via HTTP, it can store information in a MySQL database, and it
exposes a thrift RPC api to answer questions about the data it has stored. Of
course, as this all builds as a single unit, all the dependencies are shared
and nothing prevents code reuse between any two parts of the project. In fact,
nothing prevents circular references between classes or mutual dependencies
between packages. Let's do something about that.

First up, let's include the `halp` jar. In maven it looks like this:

```
<dependency>
    <groupId>com.coxautodev</groupId>
    <artifactId>halp</artifactId>
    <version>0.9.0</version>
    <scope>test</scope>
</dependency>
```

Then, let's create a unit test to avoid circular dependencies between classes
and between packages.

```
import static dependency.spec.Assertions.*;
import static dependency.spec.Core.*;
import static dependency.spec.Modules.*;

public class ModuleTest {

    @Test
    public void enforceModuleSystem() throws IOException {
        List<ClassInfo> cp = analyzeClasspath("blitzen.**");
        assertNoClassCycles(cp);
        assertNoPackageCycles(cp);
    }
```

This test analyzes the project classpath, and then verifies that no circular
dependencies exist in that scanned code. Let's try some module definitions:

```
@Test
public void enforceModuleSystem() throws IOException {

    List global = asList("org.slf4j.**", "ports.*.*");

    Set<Module> modules = modules(

        module("ports")
            .include("ports.*.*")
            .use(global),

        module("core")
            .include("core.**")
            .use(global, "com.fasterxml.jackson.**"),

        module("db-mysql")
            .include("ports.db.mysql.**")
            .use(global, "org.springframework.jdbc.**", "org.apache.tomcat.jdbc.**"),

        module("gateway-http")
            .include("ports.gateway.keymetric.**")
            .use(global, "org.eclipse.jetty.**", "org.apache.commons.io.**"),

        module("api-thrift")
            .include("ports.api.thrift.**")
            .use(global, "org.apache.thrift.**"),

        module("app")
            .include("app.**")
            .use(global, "**")
        );

    List<ClassInfo> cp = analyzeClasspath("blitzen.**");
    assertModuleBoundaries(cp, modules);
    assertNoUnmodularizedBehavior(cp, modules);
}
```

This test verifies that all the specified modules only use what they're
expicitly using here by calling `assertModuleBoundaries`. It also ensures that
all code beneath the `blitzen` package has been assigned a module by calling
`assertNoUnmodularizedBehavior`.

Hopefully this is enough of an example to get you started. To see this work
in action, take a look at the unit tests for this project.


Why Does Anyone Need This?
--------------------------

Many language systems have an explicit Module concept that is built-in to the
language.  Java has no such system, though various ways of achieving this have
cropped up over the years.  

What is a module? Essentially, it is a body of behavior which is abstracted
behind an exported contract. The contract represents the functionality
provided, as well as the transitive dependencies required to make use of the
contract. A large software system cannot be sustainably modeled without
breaking it down into such components.

The simplest way to simulate a module in Java is by using the access modifiers
which are built-in to the language. However, this has many downsides. Such
usage can be trivially circumvented, even by accident. Also, there are many
ways of using access modifiers such that the meaning of a modifier is often not
very clear (beyond its immediate implications). Effective use of them for
the purpose of modularity can make testing difficult and awkward.  In short,
java access modifiers are not a sufficient module definition language.

Without creating such a native language, there are ways to use the build system
to break down an application's modules into individually compiled units with
clearly-defined dependencies between them. You can do this easily enough, and
often in build systems like maven this is the simplest way to achieve this kind
of clean separation. In Maven, this can be cumbersome for small projects. This
is because the number of files and folders needed to represent a sub-module of
a project is not small, and may outnumber the actual source files in the
overall application. There is also a fair amount of xml involved to maintain
such a structure, which can feel like overkill.

There are much more sophisticated approaches, the most famous of which is OSGI.
OSGI is a standard originally constructed to run software on TV set-top boxes.
It is designed to run arbitrary modules with completely disparate and conflicting
dependencies within a single JVM cleanly and sustainably. It uses sophisticated
classloading tricks to achieve this, with all the downsides those entail. It can
be hard to reason about what such an app is doing at runtime, and it complicates
the building of software because it layers its own module assembly system on top
of whatever build system is already in use. Really, the overhead of using such
a system is only justified when you are in unenviable position of having to load
arbitrary code into your application without having identified it ahead of time.
A typical example of this type of application is an IDE that supports plugins and 
extensions. Eclipse uses OSGI internally.

This project exists as a middle-ground between plain java access modifiers and
heavier-weight options. It allows you to put all your code in one root project,
but still write unit tests that define module boundaries. It permits you to
assert that the boundaries are intact and that no module depends on things is
not explicitly permitted to use.

With any luck, this project will make modularity-first seem manageable from
the start of a project, when such boundaries are easy to enforce and make a
part of the system's design.
