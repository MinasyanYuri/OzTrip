import os
import re
import xml.etree.ElementTree as ET

# Папка с layout-файлами (можно расширить на все XML, если нужно)
LAYOUT_DIR = "app/src/main/res/layout"
STRINGS_FILE = "app/src/main/res/values/strings.xml"

# Теги, внутри которых ищем жёсткий текст (можете дополнить)
TEXT_ATTRS = ["android:text", "android:hint", "android:contentDescription", "android:title"]

# Собираем все строки и запоминаем их позиции
counter = 1
string_map = {}           # (текст) -> имя ресурса
replacements = []         # (путь к файлу, номер строки, старое значение, новое @string/...)
 
for root_dir, dirs, files in os.walk(LAYOUT_DIR):
    for file in files:
        if not file.lower().endswith(".xml"):
            continue
        path = os.path.join(root_dir, file)
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        modified = False
        for attr in TEXT_ATTRS:
            # Ищем атрибут="..." (не @string и не ?)
            pattern = rf'({attr})=(")([^"@?][^"]*)(")'
            for match in re.finditer(pattern, content):
                original = match.group(3)
                if original not in string_map:
                    # Присваиваем имя text_1, text_2 ...
                    string_map[original] = f"text_{counter}"
                    counter += 1
                res_name = string_map[original]
                replacement = f'{attr}="@string/{res_name}"'
                # Заменяем в контенте (простая замена, но осторожно)
                content = content.replace(match.group(0), replacement)
                modified = True
        if modified:
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)

# Создаём / обновляем strings.xml
os.makedirs(os.path.dirname(STRINGS_FILE), exist_ok=True)
if os.path.exists(STRINGS_FILE):
    # Загрузим существующие строки, чтобы не потерять
    tree = ET.parse(STRINGS_FILE)
    root = tree.getroot()
else:
    root = ET.Element("resources")

# Добавляем новые строки
for text, name in string_map.items():
    # Избегаем дублирования
    existing = root.find(f"./string[@name='{name}']")
    if existing is not None:
        existing.text = text
    else:
        elem = ET.SubElement(root, "string", {"name": name})
        elem.text = text

# Красиво записываем (с отступами)
ET.indent(root, space="    ")
tree = ET.ElementTree(root)
tree.write(STRINGS_FILE, encoding="utf-8", xml_declaration=True)

print(f"Готово! Обработано {len(string_map)} уникальных строк.")
print(f"Макеты обновлены, strings.xml сохранён: {STRINGS_FILE}")