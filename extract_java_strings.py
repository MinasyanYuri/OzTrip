import os
import re
import xml.etree.ElementTree as ET
from xml.dom import minidom

JAVA_SRC = "app/src/main/java/com/example/oztrip"
STRINGS_FILE = "app/src/main/res/values/strings.xml"

RUSSIAN_CHAR = re.compile(r'[а-яёА-ЯЁ]')
STRING_RE = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')

def collect_and_replace_java_files():
    # Определяем следующий доступный индекс, начиная с максимального text_N в files
    tree = ET.parse(STRINGS_FILE)
    root = tree.getroot()
    max_idx = 0
    for elem in root.findall("string"):
        name = elem.get("name")
        # Находим все text_<число> и text_auto_<число>
        m = re.match(r"^text(?:_auto)?_(\d+)$", name)
        if m:
            idx = int(m.group(1))
            if idx > max_idx:
                max_idx = idx
    # Начнём с max_idx+1, чтобы не пересекаться
    counter = max_idx
    new_strings = {}

    for root_dir, dirs, files in os.walk(JAVA_SRC):
        for file in files:
            if not file.endswith(".java"):
                continue
            path = os.path.join(root_dir, file)
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()

            modified = False
            for match in STRING_RE.finditer(content):
                original = match.group(1)
                if not RUSSIAN_CHAR.search(original):
                    continue
                if original.strip().startswith("@string/"):
                    continue
                if original not in new_strings:
                    counter += 1
                    new_strings[original] = f"text_{counter}"   # <- теперь text_89, text_90...
                res_name = new_strings[original]
                replacement = f'getString(R.string.{res_name})'
                content = content.replace(f'"{original}"', replacement)
                modified = True

            if modified:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(content)

    if not new_strings:
        print("Не найдено новых строк в Java-коде, содержащих русские буквы.")
        return

    for text, name in new_strings.items():
        # Проверка на дубликат (на всякий случай)
        existing = root.find(f"./string[@name='{name}']")
        if existing is not None:
            existing.text = text
        else:
            elem = ET.SubElement(root, "string", {"name": name})
            elem.text = text

    xml_str = ET.tostring(root, encoding="utf-8")
    dom = minidom.parseString(xml_str)
    pretty_xml = dom.toprettyxml(indent="    ")
    lines = pretty_xml.splitlines()
    filtered = [line for line in lines if not line.strip().startswith('<?xml')]
    with open(STRINGS_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(filtered))

    print(f"Готово! Добавлено {len(new_strings)} новых строк. Имена: text_{max_idx+1} ... text_{counter}")

if __name__ == "__main__":
    collect_and_replace_java_files()