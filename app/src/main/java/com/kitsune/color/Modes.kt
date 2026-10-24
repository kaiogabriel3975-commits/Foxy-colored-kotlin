package com.kitsune.color

class ModeGroup(val name: String, val modes: List<Pair<Int, String>>)

private fun grp(name: String, start: Int, names: List<String>) =
    ModeGroup(name, names.mapIndexed { i, n -> (start + i) to n })

val MODE_GROUPS = listOf(
    ModeGroup(
        "Básicos",
        listOf(
            1 to "Ciclo automático", 2 to "Sinfonia", 3 to "Energia colorida", 4 to "Saltos coloridos",
            7 to "Pisca 7 cores", 10 to "Degradê 7 cores", 26 to "Esvoaçar colorido", 27 to "Esvoaçar RGB",
            29 to "Pincelada colorida", 30 to "Pincelada RGB", 31 to "Pincelada amarelo-ciano-roxo", 32 to "Pincelada com retorno",
        ),
    ),
    grp("Abre e fecha", 35, listOf("7 cores", "Vermelho-verde-azul", "Amarelo-ciano-roxo", "Vermelho", "Verde", "Azul", "Amarelo", "Ciano", "Roxo", "Branco")),
    grp("Transição", 45, listOf("7 cores", "Azul-vermelho-verde", "Violeta-verde-amarelo", "Vermelho", "Verde", "Azul", "Ciano", "Amarelo", "Roxo", "Branco")),
    grp("Água corrente", 55, listOf("7 cores", "Azul-verde-vermelho", "Roxo-verde-amarelo", "Vermelho-verde", "Verde-azul", "Amarelo-azul", "Amarelo-ciano", "Azul-roxo")),
    grp("Rastro", 76, listOf("7 cores", "Vermelho", "Verde", "Azul", "Amarelo", "Ciano", "Roxo", "Branco")),
    grp("Corrida", 84, listOf("Vermelho", "Verde", "Azul", "Amarelo", "Ciano", "Roxo", "Branco", "7 cores", "Azul-verde-vermelho", "Roxo-ciano-amarelo")),
)

/** IDs que existem no firmware (strings do app original) mas não têm tela própria. */
val MODE_EXTRA = ModeGroup(
    "Experimentais",
    (63..75).map { it to "Modo $it" } + (94..117).map { it to "Modo $it" },
)
