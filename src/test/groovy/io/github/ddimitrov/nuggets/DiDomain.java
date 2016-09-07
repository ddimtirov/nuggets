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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiDomain {
    public interface IXyzzyService {
        void addListener(XyzzyListener listener);
        void broadcast(String id);
    }

    public interface XyzzyListener {
        void onEvent(String id);
    }

    public interface IFooBarStrategy extends XyzzyListener {
        void doIt();
    }

    public static class XyzzyServiceImpl implements IXyzzyService, Closeable {
        final List<XyzzyListener> listeners = new ArrayList<>();
        @Override
        public void close() throws IOException { }
        @Override
        public void addListener(XyzzyListener listener) {
            listeners.add(listener);
        }
        @Override
        public void broadcast(String id) {
            listeners.forEach(it -> it.onEvent(id));
        }

    }

    public static class FooBarLocalImpl implements IFooBarStrategy {
        public boolean doneIt;
        public Map<String, Integer> notifs = new HashMap<>();

        @Override
        public void onEvent(String id) {
            notifs.compute(id, (key, old) -> old==null ? 1 : old+1);
        }

        @Override
        public void doIt() {
            doneIt = true;
        }
    }

    public static class MyApplication {
        public Boolean running;
        public final IXyzzyService xyzzy;
        public final IFooBarStrategy foobar;

        public MyApplication(IXyzzyService xyzzy, IFooBarStrategy foobar) {
            this.xyzzy = xyzzy;
            this.foobar = foobar;
        }

        public void run() {
            running = true;
            foobar.doIt();
            xyzzy.broadcast("run");
        }

        public void shutdown() {
            running = false;
            xyzzy.broadcast("done");
            xyzzy.broadcast("done");
        }
    }
}
