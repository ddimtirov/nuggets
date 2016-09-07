/*
 *    Copyright 2016 by Dimitar Dimitrov
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.github.ddimitrov.nuggets;

import javax.inject.Inject;
import java.util.Date;

public class InjectableDomain {
    public static class NoAnnotatedConstructor {
        public final Date foobar;
        public final String bazqux;

        public NoAnnotatedConstructor() {
            this(null);
        }

        public NoAnnotatedConstructor(Date foobar) {
            this(foobar, "injected");
        }

        public NoAnnotatedConstructor(Date foobar, String bazqux) {
            this.foobar = foobar;
            this.bazqux = bazqux;
        }

        public void notInjectable() {

        }
    }

    public static class WithAnnotatedConstructor {
        public final Date foobar;
        public final String bazqux;

        public WithAnnotatedConstructor() {
            this(null);
        }

        @Inject
        public WithAnnotatedConstructor(Date foobar) {
            this(foobar, "injected");
        }

        public WithAnnotatedConstructor(Date foobar, String bazqux) {
            this.foobar = foobar;
            this.bazqux = bazqux;
        }

        @Inject
        public void injectable(int i) {

        }

        @Inject
        private void injectablePrivate(Date d) {

        }

        @Inject
        public void notInjectableNoArgs() {

        }

        public void notInjectableNoAnnotation() {

        }
    }

    @SuppressWarnings("MultipleInjectedConstructorsForClass")
    public static class WithManyAnnotatedConstructors {
        public final Date foobar;
        public final String bazqux;

        public WithManyAnnotatedConstructors() {
            this(null);
        }

        @Inject
        public WithManyAnnotatedConstructors(Date foobar) {
            this(foobar, "injected");
        }

        @Inject
        public WithManyAnnotatedConstructors(Date foobar, String bazqux) {
            this.foobar = foobar;
            this.bazqux = bazqux;
        }


        @Inject
        public void injectable(int i, double d) {

        }

        @Inject
        private void injectablePrivate(Date d) {

        }
    }
}
