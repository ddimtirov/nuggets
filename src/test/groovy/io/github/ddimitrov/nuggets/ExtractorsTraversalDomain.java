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

public class ExtractorsTraversalDomain {
    static class CA extends C4 implements I4 {
        public CA() { super(0, 0); }
        public void bing() { }
    }

    static class CB extends C3 implements I3 {
        public CB() { super(0); }
        public CB(int i) { super(i); }

        public void bing() { }
        public void beng() { }
    }

    static class CC implements I1, I2 {
        public void bang() { }
        public void bong() { }
        public String toString() { return super.toString(); }
    }

    interface I1 {
        void bang();
        void bong();
    }
    interface I2 extends I1 {}
    interface I3 extends I1 {
        void bang();
        void bing();
        default void beng() {}
    }
    interface I4 extends I2, I3{}

    static class C1 {
        private final int i;
        protected C1(int i) { this.i = i; }
        private static void foo() {}
    }
    static class C2 extends C1 implements I2 {
        C2(double i) { super((int) i); }
        C2(int i) { super(i); }
        C2() { super(1); }
        public void bang() { }
        public void bong() { }
    }
    static class C3 extends C2 {
        private final int i;
        protected C3(int i) { this.i = i; }
        private void bazz() {}
        private static void foo() {}
    }
    static class C4 extends C3 {
        public C4(int i, double d) { super(i); }
    }
}
