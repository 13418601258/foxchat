package com.wjy.foxchat.data.pet

import com.wjy.foxchat.data.local.PetEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 宠物状态引擎：所有时间一律用真实时间（System.currentTimeMillis()）。
 *
 * 规则：
 *  - 每超过 8 小时未互动，食物/喝水各 -1
 *  - 食物/喝水 < 4：健康明显下降；< 6：轻微下降；≥ 6：缓慢回升
 *  - 喂食：食物/喝水 +1（封顶 10）、亲密度 +1（封顶 100）、健康小幅回升
 *  - 玩耍：亲密度 +1（封顶 100）
 *  - 所有数值带封顶，不越界
 */
object PetManager {

    const val MAX_STAT = 10
    const val MAX_LOVE = 100
    private const val HOUR_MS = 60L * 60L * 1000L
    private const val HUNGER_INTERVAL_HOURS = 8L

    /** 结算：按真实流逝时间更新状态。返回新的宠物状态。 */
    fun settle(pet: PetEntity, now: Long = System.currentTimeMillis()): PetEntity {
        val elapsedMs = (now - pet.lastUpdatedAt).coerceAtLeast(0L)
        val elapsedHours = elapsedMs / HOUR_MS

        var food = pet.food
        var drink = pet.drink

        // 超过 8 小时未管它，每满 8 小时扣 1（向下取整，最低 0）
        if (elapsedHours >= HUNGER_INTERVAL_HOURS) {
            val hungerUnits = (elapsedHours / HUNGER_INTERVAL_HOURS).toInt()
            food = (food - hungerUnits).coerceAtLeast(0)
            drink = (drink - hungerUnits).coerceAtLeast(0)
        }

        // 跨天天数
        val days = pet.days + daysBetween(pet.lastUpdatedAt, now)

        // 健康：饥饿下降 / 充足回升
        val condition = when {
            food < 4 || drink < 4 -> pet.condition - 0.1
            food < 6 || drink < 6 -> pet.condition - 0.01
            else -> pet.condition + 0.05
        }.coerceIn(0.0, MAX_STAT.toDouble())

        return pet.copy(
            food = food,
            drink = drink,
            condition = condition,
            days = days,
            lastUpdatedAt = now
        )
    }

    /** 喂食：先结算，再恢复食物/喝水/亲密度/健康。 */
    fun feed(pet: PetEntity, now: Long = System.currentTimeMillis()): PetEntity {
        val settled = settle(pet, now)
        return settled.copy(
            food = (settled.food + 1).coerceAtMost(MAX_STAT),
            drink = (settled.drink + 1).coerceAtMost(MAX_STAT),
            love = (settled.love + 1).coerceAtMost(MAX_LOVE),
            condition = (settled.condition + 0.05).coerceAtMost(MAX_STAT.toDouble()),
            lastUpdatedAt = now
        )
    }

    /** 玩耍：先结算，再提升亲密度。 */
    fun play(pet: PetEntity, now: Long = System.currentTimeMillis()): PetEntity {
        val settled = settle(pet, now)
        return settled.copy(
            love = (settled.love + 1).coerceAtMost(MAX_LOVE),
            lastUpdatedAt = now
        )
    }

    private fun daysBetween(from: Long, to: Long): Int {
        if (from <= 0 || to <= 0) return 0
        val fromDate = Instant.ofEpochMilli(from).atZone(ZoneId.systemDefault()).toLocalDate()
        val toDate = Instant.ofEpochMilli(to).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(fromDate, toDate).toInt().coerceAtLeast(0)
    }
}
