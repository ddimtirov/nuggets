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

package io.github.ddimitrov.nuggets.internal.groovy;

import groovy.lang.Closure;

@SuppressWarnings("MethodDoesntCallSuperMethod")
public abstract class DelegatedClosure<T> extends Closure<T> {
    private static final long serialVersionUID = -255672061147963988L;
    private final Closure<T> self;

    public DelegatedClosure(Closure<T> self) {
        super(self.getOwner(), self.getThisObject());
        this.self = self;
    }

    @Override public T          call(Object... args)                                          { return self.call(args); }
    @Override public Closure<?> asWritable()                                                  { return self.asWritable(); }
    @Override public Closure<T> dehydrate()                                                   { return self.dehydrate(); }
    @Override public Closure<T> rehydrate(Object delegate, Object owner, Object thisObject)   { return self.rehydrate(delegate, owner, thisObject); }
    @Override public Object     clone()                                                       { return self.clone(); }
    @Override public boolean    isCase(Object candidate)                                      { return self.isCase(candidate); }
    @Override public Object     getThisObject()                                               { return self.getThisObject(); }
    @Override public Object     getOwner()                                                    { return self.getOwner(); }
    @Override public Class<?>[] getParameterTypes()                                           { return self.getParameterTypes(); }
    @Override public int        getMaximumNumberOfParameters()                                { return self.getMaximumNumberOfParameters(); }
    @Override public Object     getProperty(String property)                                  { return self.getProperty(property); }
    @Override public void       setProperty(String property, Object newValue)                 {        self.setProperty(property, newValue); }
    @Override public int        getResolveStrategy()                                          { return self.getResolveStrategy(); }
    @Override public void       setResolveStrategy(int resolveStrategy)                       {        self.setResolveStrategy(resolveStrategy); }
    @Override public Object     getDelegate()                                                 { return self.getDelegate(); }
    @Override public void       setDelegate(Object delegate)                                  {        self.setDelegate(delegate); }
    @Override public int        getDirective()                                                { return self.getDirective(); }
    @Override public void       setDirective(int directive)                                   {        self.setDirective(directive); }
}
