package com.coxautodev.halp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class Modules {

    public interface Builder {
        Builder include(String... includes);
        Builder use(Object... uses);
        Module build();
    }

    private static class BuilderImpl implements Builder {

        private String name;
        private final Set<String> includes = new HashSet();
        private final Set<String> uses = new HashSet();

        BuilderImpl(String name) {
            this.name = name;
        }

        /**
         * Defines the classes that make up this module
         */
        public Builder include(String...includes) {
            this.includes.addAll(asList(includes));
            return this;
        }

        /**
         * Defines the dependencies this module is allowed to have
         */
        public Builder use(Object...uses) {
            for (Object use : uses) {
                if (use instanceof String) {
                    this.uses.add((String)use);
                }
                else if (use instanceof Collection) {
                    for (Object o : (Collection)use) {
                        use(o);
                    }
                }
            }
            return this;
        }

        public Module build() {
            return new Module() {

                public String name() { return name; }

                public List<String> includes() { return new ArrayList(includes); }

                public List<String> uses() { return new ArrayList(uses); }
            };
        }
    }

    public static Builder module(String name) { return new BuilderImpl(name); }

    public static Set<Module> modules(Builder...builders) {
        Set<Module> modules = new HashSet();
        for (Builder b : builders) {
            modules.add(b.build());
        }
        return modules;
    }

    public static Set<String> includes(Iterable<Module> modules) {
        Set<String> includes = new HashSet();
        modules.forEach(module -> includes.addAll(module.includes()));
        return includes;
    }
}
