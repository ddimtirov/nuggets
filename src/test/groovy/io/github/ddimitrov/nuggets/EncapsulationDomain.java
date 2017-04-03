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

import java.util.Date;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class EncapsulationDomain {
    public static final int inlineableConstantField = 42;
    public static final Object finalStaticField = new Object();
    private static final int privateInlineableConstantField = 12;
    private static final Object privateFinalStaticField = new Object();

    /** used by the tests to undo the damage */
    public static void resetConstants() {
        Class<?> ed = EncapsulationDomain.class;
        Extractors.pokeField(null, ed, "inlineableConstantField",42);
        Extractors.pokeField(null, ed, "privateInlineableConstantField",12);
        Extractors.pokeField(null, ed, "privateFinalStaticField",new Object());
        Extractors.pokeField(null, ed, "finalStaticField",new Object());
    }

    private long foo(Date a, int b, String c) { return 64; }

    public final int finalField;
    private final int privateFinalField;
    private int privateField = 8;
    private int unshadowed = 5;

    public EncapsulationDomain(int finalField, int privateFinalField) {
        this.finalField = finalField;
        this.privateFinalField = privateFinalField;
    }

    public static int getPublicConstant() {
        return inlineableConstantField;
    }
    public static int getPrivateConstant() {
        return privateInlineableConstantField;
    }

    public InnerClass createInner() { return new InnerClass();}
    public class InnerClass {

    }

    public static class ShadowingSubclass extends EncapsulationDomain {
        public final int finalField;
        private final int privateFinalField;
        private int privateField = 2;

        public ShadowingSubclass(int parentFinalField, int parentPrivateFinalField, int finalField, int privateFinalField) {
            super(parentFinalField, parentPrivateFinalField);
            this.finalField = finalField;
            this.privateFinalField = privateFinalField;
        }

        public static int getPublicConstant() {
            return -inlineableConstantField;
        }
    }
}
