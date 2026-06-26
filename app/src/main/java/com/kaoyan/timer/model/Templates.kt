package com.kaoyan.timer.model

object Templates {

    data class Tpl(
        val label: String,
        val note: String,
        val groups: List<Pair<String, List<String>>>
    )

    val all: Map<String, Tpl> = mapOf(
        "11408" to Tpl(
            label = "11408 学硕",
            note = "数一/408/英一",
            groups = listOf(
                "数学一" to listOf("高数", "线代", "概率"),
                "408" to listOf("数据结构", "计算机组成原理", "操作系统", "计算机网络"),
                "英语一" to listOf("单词", "阅读", "新题型", "翻译", "写作"),
                "政治" to listOf("政治")
            )
        ),
        "22048" to Tpl(
            label = "22048 专硕",
            note = "数二/408/英二",
            groups = listOf(
                "数学二" to listOf("高数", "线代"),
                "408" to listOf("数据结构", "计算机组成原理", "操作系统", "计算机网络"),
                "英语二" to listOf("单词", "阅读", "翻译", "写作"),
                "政治" to listOf("政治")
            )
        )
    )

    fun build(key: String): List<Subject> {
        val tpl = all[key] ?: return emptyList()
        return tpl.groups.mapIndexed { gi, (groupName, items) ->
            Subject(
                name = groupName,
                items = items.mapIndexed { ii, itemName ->
                    Item(id = "${gi}_$ii", name = itemName)
                }
            )
        }
    }
}
