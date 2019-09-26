/*
 * Copyright (c) 2019  RS485
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0.1, or MMPL. Please check the contents of the license located in
 * https://github.com/RS485/LogisticsPipes/blob/dev/LICENSE.md
 *
 * This file can instead be distributed under the license terms of the
 * MIT license:
 *
 * Copyright (c) 2019  RS485
 *
 * This MIT license was reworded to only match this file. If you use the regular
 * MIT license in your project, replace this copyright notice (this line and any
 * lines below and NOT the copyright line above) with the lines from the original
 * MIT license located here: http://opensource.org/licenses/MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this file and associated documentation files (the "Source Code"), to deal in
 * the Source Code without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Source Code, and to permit persons to whom the Source Code is furnished
 * to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Source Code, which also can be
 * distributed under the MIT.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package network.rs485.logisticspipes.transport

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import network.rs485.logisticspipes.transport.network.ConnectionContext

interface Pipe<P : CellPath, X> {

    /**
     * How fast cells flow through this pipe. 1.0 is normal speed
     */
    @JvmDefault
    fun getSpeedFactor(): Float = 1.0f

    /**
     * Gets called when a cell is requested to enter this pipe.
     * Insert the cell into this pipe properly here, with the correct path.
     */
    fun onEnterPipe(network: PipeNetwork, from: X, cell: Cell<*>)

    /**
     * Gets called when a cell has reached the end of its path.
     */
    fun onFinishPath(network: PipeNetwork, path: P, cell: Cell<*>)

    fun discoverNeighbors(ctx: ConnectionContext<X>)

    /**
     * Maps position + side to one of the pipe's ports.
     */
    fun getPort(pos: BlockPos, side: Direction): X?

}

// test/demo classes

abstract class StandardPipe(val world: World) : Pipe<StandardPipeCellPath, Direction> {

    override fun onEnterPipe(network: PipeNetwork, from: Direction, cell: Cell<*>) {
        // Send the cell inwards, from the side it entered from.
        network.insert(cell, this, StandardPipeCellPath(from, true))
    }

    abstract fun routeCell(network: PipeNetwork, from: Direction, cell: Cell<*>): Direction?

    override fun onFinishPath(network: PipeNetwork, path: StandardPipeCellPath, cell: Cell<*>) {
        if (path.inwards) {
            // The cell has reached the center of the pipe
            val outputSide = routeCell(network, path.side, cell)

            if (outputSide == null) {
                // If there's nowhere to go, drop the cell as an entity in the world, if possible.
                // And remove it from the network, of course
                val content = network.untrack(cell)
                val entity = content.createEntity(world) // TODO: set position of entity?
                if (entity != null) world.spawnEntity(entity)
            } else {
                // Continue on, to infinity and beyond! Uhh, I mean, in the random direction we picked.
                network.insert(cell, this, StandardPipeCellPath(outputSide, false))
            }
        } else {
            // The cell has reached the end of the pipe.
            val nextPipe = network.getConnectedPipe(this, path.side)
            if (nextPipe != null) {
                // If there's a pipe connected to this one at the side the item is supposed to come out of (which it should), put it in there
                network.insert(cell, nextPipe, path.side.opposite)
            } else {
                // Otherwise, again, drop the item.
                val content = network.untrack(cell)
                val entity = content.createEntity(world)
                if (entity != null) world.spawnEntity(entity)
            }
        }
    }

    override fun discoverNeighbors(ctx: ConnectionContext<Direction>) {
        TODO("not implemented")
    }

    override fun getPort(pos: BlockPos, side: Direction): Direction? {
        // if pos == this.pos?
        return side
    }

}

// Unrouted pipes (and routed pipes) have 12 different paths in them that items can go
// (6 sides, either from center -> edge or edge -> center), as opposed to highspeed tubes, which only have 2 possible paths
// (either "forwards" or "backwards"), since those don't have any intersections that items can branch off of.

class UnroutedPipe(val connectedSides: Set<Direction>, world: World) : StandardPipe(world) {

    override fun routeCell(network: PipeNetwork, from: Direction, cell: Cell<*>): Direction? {
        // Take a random side out of the sides that the cell does not come from (so that it doesn't go backwards), and send it in that direction.
        val possibleSides = connectedSides - from
        val outputSide = if (possibleSides.isNotEmpty()) possibleSides.random() else null
        return outputSide
    }

}