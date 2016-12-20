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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.function.Function;

import static io.github.ddimitrov.nuggets.Exceptions.rethrow;

class FunctionsJavaUsage {

    public static Function<String, Object> parseAnything() {
        return Functions.fallback(false, null,
                Long::parseLong,
                Double::parseDouble,
                it -> rethrow(()-> new SimpleDateFormat("yyyy-MM-dd").parse(it)),
                it -> {
                    String[] arr = it.trim().split("\\s*,\\s*");
                    if (arr.length==1) throw new IllegalArgumentException("not an array: " + it);
                    return Arrays.asList(arr);
                },
                it -> it
        );
    }

    public static Function<String, Number> parsePositiveNumber() {
        return Functions.fallback(false, it -> it.doubleValue()>0,
                Long::parseLong,
                Double::parseDouble
        );
    }

    public static Function<Object, Integer> ifMappedGreaterThan(int score) {
        return Functions.fallback(true, it -> it!=null && it>score,
                it -> it instanceof Integer ? (Integer) it : null,
                it -> it instanceof String ? Integer.parseInt((String) it) * 2: null,
                it -> Integer.parseInt(it.toString())
        );
    }

}
