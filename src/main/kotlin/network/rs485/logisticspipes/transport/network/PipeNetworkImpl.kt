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

package network.rs485.logisticspipes.transport.network

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Direction
import network.rs485.logisticspipes.transport.*
import java.util.*

class PipeNetworkImpl(val world: ServerWorld, override val id: UUID) : PipeNetwork {

    private val updateTimes = Object2LongOpenHashMap<UUID>()

    private val cellPositions = mutableMapOf<UUID, AbsoluteCellPosition<*>>()
    private val cellMap = mutableMapOf<UUID, Cell<*>>()

    override val pipes = mutableSetOf<Pipe<*>>()

    override val cells
        get() = cellMap.values.toSet()

    override fun <P : CellPath> insert(cell: Cell<*>, pipe: Pipe<P>, path: P) {
        cellPositions[cell.id] = AbsoluteCellPosition(pipe, path)
        cellMap[cell.id] = cell

        val speed = BASE_SPEED * pipe.getSpeedFactor() * cell.getSpeedFactor()
        val length = path.getLength()
        val time = length / speed
        updateTimes[cell.id] = world.time + time.roundToLong()
    }

    override fun <T : CellContent> untrack(cell: Cell<T>): T {
        untrack0(cell)
        return cell.content
    }

    private fun untrack0(cell: Cell<*>) {
        val cellId = cell.id
        updateTimes.removeLong(cellId)
        cellPositions.remove(cellId)
        cellMap.remove(cellId)
    }

    fun tick() {
        val iter = updateTimes.iterator()
        for ((id, t) in iter) {
            if (t < world.time) {
                run {
                    val cell = cellMap[id] ?: return@run
                    val cp = cellPositions[id] ?: return@run
                    cp.onFinish(this, cell)
                }
                iter.remove()
            } else break
        }
    }

    override fun getConnectedPipe(self: Pipe<*>, side: Direction): Pipe<*>? {
        TODO("not implemented")
    }

    fun toTag(tag: CompoundTag = CompoundTag()): CompoundTag {
        return tag
    }

    fun fromTag(tag: CompoundTag) {
        val newId = tag.getUuid("id")
        if (newId != id) error("Tried to load data for $newId into network $id")

    }

    companion object {
        const val BASE_SPEED = 0.1f

        fun fromTag(world: ServerWorld, tag: CompoundTag): PipeNetworkImpl {
            val id = tag.getUuid("id")
            val obj = PipeNetworkImpl(world, id)
            obj.fromTag(tag)
            return obj
        }
    }

}

private data class AbsoluteCellPosition<P : CellPath>(val pipe: Pipe<P>, val path: P) {
    // Helper method because of generics bs.
    fun onFinish(network: PipeNetwork, cell: Cell<*>) {
        pipe.onFinishPath(network, path, cell)
    }
}