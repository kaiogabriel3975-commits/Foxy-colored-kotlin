from pathlib import Path

ROOT = Path(__file__).resolve().parent
FILES = {
    "app/src/main/java/com/kitsune/color/Prefs.kt": [
        ("fun setWallBlur(", "fun updateWallBlur("),
        ("fun setLedCount(", "fun updateLedCount("),
        ("fun setLedOrder(", "fun updateLedOrder("),
        ("fun setLastScreen(", "fun updateLastScreen("),
    ],
    "app/src/main/java/com/kitsune/color/LedController.kt": [
        ("fun setBright(", "fun updateBright("),
        ("fun setPower(", "fun updatePower("),
    ],
    "app/src/main/java/com/kitsune/color/Screens.kt": [
        ("led.setBright(", "led.updateBright("),
        ("led.setPower(", "led.updatePower("),
        ("p.setWallBlur(", "p.updateWallBlur("),
        ("p.setLedCount(", "p.updateLedCount("),
        ("p.setLedOrder(", "p.updateLedOrder("),
    ],
    "app/src/main/java/com/kitsune/color/App.kt": [
        ("prefs.setLastScreen(", "prefs.updateLastScreen("),
    ],
}

for rel, replacements in FILES.items():
    path = ROOT / rel
    if not path.exists():
        raise FileNotFoundError(f"Arquivo não encontrado: {rel}")

    text = path.read_text(encoding="utf-8")
    original = text

    for old, new in replacements:
        text = text.replace(old, new)

    if text == original:
        print(f"[sem alteração] {rel}")
    else:
        path.write_text(text, encoding="utf-8")
        print(f"[corrigido] {rel}")

print("\nCorreção concluída.")
