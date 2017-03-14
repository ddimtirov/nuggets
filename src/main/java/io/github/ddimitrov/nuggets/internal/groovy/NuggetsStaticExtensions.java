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

package io.github.ddimitrov.nuggets.internal.groovy;

import io.github.ddimitrov.nuggets.Ports;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;

import static io.github.ddimitrov.nuggets.Exceptions.rethrow;

/** Extra API to make Groovy usage nicer */
public class NuggetsStaticExtensions {
    private NuggetsStaticExtensions() { }

    /**
     * A convenient factory method creates a ports allocator with block registrar and default dynamic port allocation strategy.
     * This allocator will try to bind a port at `lockOffset` and if successful will assume that the whole block is available
     * and use that block to allocate all ports based on specified offsets.
     *
     * Locks the base port, bound using the default network interface as returned by {@code InetAddress.getLocalHost()}.
     *
     * @param selfless type token for Groovy to attach the extension method to the {@code Ports} class.
     * @param portRangeSize the desired size of the allocated block. This determines at what offset will be the next block.
     */
    public static @NotNull Ports continuousBlock(@Nullable Ports selfless, int portRangeSize) {
        return continuousBlock(selfless, portRangeSize, portRangeSize, null);
    }

    /**
     * A convenient factory method creates a ports allocator with block registrar and default dynamic port allocation strategy.
     * This allocator will try to bind a port at `lockOffset` and if successful will assume that the whole block is available
     * and use that block to allocate all ports based on specified offsets.
     *
     * The lock port is bound using the default network interface as returned by {@code InetAddress.getLocalHost()}.
     *
     * @param selfless type token for Groovy to attach the extension method to the {@code Ports} class.
     * @param portRangeSize the desired size of the allocated block. This determines at what offset will be the next block.
     * @param lockOffset the offset of the port that we will bind in order to lock the range. Relative to the range base port.
     *                   Valid values are `-1..portRangeSize`
     */
    public static @NotNull Ports continuousBlock(@Nullable Ports selfless, int portRangeSize, int lockOffset) {
        return continuousBlock(selfless, portRangeSize, lockOffset, null);
    }

    /**
     * A convenient factory method creates a ports allocator with block registrar and default dynamic port allocation strategy.
     * This allocator will try to bind a port at `lockOffset` and if successful will assume that the whole block is available
     * and use that block to allocate all ports based on specified offsets.
     *
     * @param selfless type token for Groovy to attach the extension method to the {@code Ports} class.
     * @param portRangeSize the desired size of the allocated block. This determines at what offset will be the next block.
     * @param lockOffset the offset of the port that we will bind in order to lock the range. Relative to the range base port.
     *                   Valid values are `-1..portRangeSize`
     * @param bindAddress the address of the network interface which we would use for locking the range.
     *                    If {@code null}, defaults to {@code InetAddress.getLocalHost()}.
     */
    public static @NotNull Ports continuousBlock(@Nullable Ports selfless, int portRangeSize, int lockOffset, @Nullable InetAddress bindAddress) {
        try {
            InetAddress address = bindAddress == null ? InetAddress.getLocalHost() : bindAddress;
            return new Ports(new Ports.BlockRegistrar(address, portRangeSize, lockOffset));
        } catch (IOException e) {
            return rethrow(e);
        }
    }
}

