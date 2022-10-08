/*
 * Copyright (c) 2021  RS485
 *
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public
 * License 1.0.1, or MMPL. Please check the contents of the license located in
 * https://github.com/RS485/LogisticsPipes/blob/dev/LICENSE.md
 *
 * This file can instead be distributed under the license terms of the
 * MIT license:
 *
 * Copyright (c) 2021  RS485
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

package network.rs485.logisticspipes.gui.widget

import net.minecraft.inventory.Slot
import network.rs485.logisticspipes.gui.*
import network.rs485.logisticspipes.gui.guidebook.Drawable

class PlayerInventorySlotGroup(
    parent: Drawable,
    xPosition: HorizontalAlignment,
    yPosition: VerticalAlignment,
    margin: Margin,
    val slots: List<Slot>
) : LPGuiWidget(
    parent = parent,
    xPosition = xPosition,
    yPosition = yPosition,
    xSize = Size.FIXED,
    ySize = Size.FIXED,
    margin = margin,
) {
    override val minWidth: Int = 9 * 18
    override val minHeight: Int = 4 * 18 + 4

    override val maxWidth: Int = minWidth
    override val maxHeight: Int = minHeight

    override fun initWidget() {
        assert(slots.size == 4 * 9)
        setSize(minWidth, minHeight)
    }

    override fun setPos(x: Int, y: Int): Pair<Int, Int> {
        super.setPos(x, y)
        val startX = absoluteBody.roundedX + 1
        val startY = absoluteBody.roundedY + 1
        val slotSize = 18
        var index = 0
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                slots[index].apply {
                    xPos = startX + column * slotSize
                    yPos = startY + row * slotSize
                }
                index++
            }
        }

        // Add the hotbar inventory slots
        for (column in 0 until 9) {
            slots[index].apply {
                xPos = startX + column * slotSize
                yPos = startY + 3 * slotSize + 4
            }
            index++
        }
        return width to height
    }
}
