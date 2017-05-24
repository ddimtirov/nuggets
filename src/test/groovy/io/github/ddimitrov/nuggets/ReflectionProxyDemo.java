/*
 *    Copyright 2017 by Dimitar Dimitrov
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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ReflectionProxyDemo {
    EncapsulationDomain.ShadowingSubclass subject = new EncapsulationDomain.ShadowingSubclass(1, 2, 3, 4);

    @Test
    public void demo() {
        ReflectionProxy rp = ReflectionProxy.wrap(subject);
        int hash = rp.get("privateFinalStaticField").invoke("hashCode").unwrap(Integer.class);
        assertNotEquals(hash, 0);

        int pff = rp.get("privateFinalField").unwrap(Integer.class);

        int pffParent = rp.resolvingAtType(EncapsulationDomain.class).get("privateFinalField").unwrap(Integer.class);
        assertEquals(pffParent, 2);
        assertEquals(pff, 4);

        rp.tap(it -> it.set("privateField", 3));
        Object x = rp.map(it -> "foo"+it.get("privateField").unwrap()).unwrap();
        assertEquals(x, "foo3");
    }
}
