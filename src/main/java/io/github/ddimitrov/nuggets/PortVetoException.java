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

import org.jetbrains.annotations.NotNull;

/**
 * Indicates that one of the exporters did not like an allocated port
 * and requests re-allocation of a new block.
 */
public class PortVetoException extends Exception {
    private static final long serialVersionUID = -6421513924172619422L;

    /**
     * The port ID that had a problem
     */
    public final String id;

    /**
     * The actual port that had a problem
     */
    public final int port;

    /**
     * Indicates that one of the exporters did not like an allocated port
     * and requests re-allocation of a new block.
     *
     * @param id the port ID that had a problem
     * @param port the actual port that had a problem
     * @param message human-readable description of the problem
     */
    public PortVetoException(@NotNull String id, int port, @NotNull String message) {
        super(message);
        this.id = id;
        this.port = port;
    }
}
